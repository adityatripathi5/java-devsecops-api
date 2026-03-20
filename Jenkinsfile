pipeline {
    agent any
    
    environment {
        GITHUB_TOKEN = credentials('GitHub-PAT-Token')
        SLACK_WEBHOOK = credentials('slack-webhook-url')
        REPO_OWNER = 'adityatripathi5'
        REPO_NAME = 'java-devsecops-api'
    }

    stages {
        stage('Dynamic Context Setup') {
            steps {
                script {
                    if (env.CHANGE_ID) {
                        echo "🚀 This is a PR RAISE! PR Number: ${env.CHANGE_ID}"
                        env.IS_PR = 'true'
                        env.GIT_COMMIT_HASH = env.GIT_COMMIT
                    } else {
                        echo "🔀 This is a DIRECT PUSH / PR MERGE to branch: ${env.BRANCH_NAME}"
                        env.IS_PR = 'false'
                        env.GIT_COMMIT_HASH = sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
                    }
                }
            }
        }

        stage('Build & SonarQube Scan') {
            steps {
                // This magically injects your URL and Token from Jenkins settings!
                withSonarQubeEnv('sonar-server') {
                    sh '''
                    # We run clean, package, and the Sonar scanner all in one command
                    mvn clean package sonar:sonar \
                      -Dsonar.projectKey=java-devsecops-api \
                      -Dsonar.projectName='java-devsecops-api' \
                      -DskipTests
                    '''
                }
            }
        }

        stage('Gitleaks Scan') {
            steps {
                sh '''
                if [ ! -f "gitleaks" ]; then
                    wget -q https://github.com/gitleaks/gitleaks/releases/download/v8.18.1/gitleaks_8.18.1_linux_x64.tar.gz
                    tar -xzf gitleaks_8.18.1_linux_x64.tar.gz
                fi
                ./gitleaks detect -v --report-format json --report-path gitleaks-report.json || true
                
                curl -s -X POST -H "Authorization: Bearer $GITHUB_TOKEN" \
                -H "Accept: application/vnd.github.v3+json" \
                https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/statuses/$GIT_COMMIT_HASH \
                -d '{"state": "success", "context": "security/gitleaks", "description": "Scan Completed"}' > /dev/null
                '''
            }
        }

        stage('Trivy Scan') {
            steps {
                sh '''
                trivy fs . --format sarif --output trivy-results.sarif || true
                
                if [ -f "trivy-results.sarif" ]; then
                    gzip -c trivy-results.sarif | base64 | tr -d '\n' > trivy-payload.b64
                    echo '{"commit_sha":"'$GIT_COMMIT_HASH'","ref":"refs/heads/'$BRANCH_NAME'","sarif":"' > trivy-req.json
                    cat trivy-payload.b64 >> trivy-req.json
                    echo '"}' >> trivy-req.json
                    
                    curl -s -X POST -H "Authorization: Bearer $GITHUB_TOKEN" \
                      -H "Accept: application/vnd.github.v3+json" \
                      https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/code-scanning/sarifs \
                      -d @trivy-req.json > /dev/null
                fi

                curl -s -X POST -H "Authorization: Bearer $GITHUB_TOKEN" \
                https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/statuses/$GIT_COMMIT_HASH \
                -d '{"state": "success", "context": "security/trivy", "description": "Scan Completed"}' > /dev/null
                '''
            }
        }

        stage('OWASP Scan') {
            steps {
                sh '''
                mvn org.owasp:dependency-check-maven:check -Dformat=SARIF -DfailBuildOnCVSS=11 || true
                
                if [ -f "target/dependency-check-report.sarif" ]; then
                    gzip -c target/dependency-check-report.sarif | base64 | tr -d '\n' > owasp-payload.b64
                    echo '{"commit_sha":"'$GIT_COMMIT_HASH'","ref":"refs/heads/'$BRANCH_NAME'","sarif":"' > owasp-req.json
                    cat owasp-payload.b64 >> owasp-req.json
                    echo '"}' >> owasp-req.json
                    
                    curl -s -X POST -H "Authorization: Bearer $GITHUB_TOKEN" \
                      -H "Accept: application/vnd.github.v3+json" \
                      https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/code-scanning/sarifs \
                      -d @owasp-req.json > /dev/null
                fi

                curl -s -X POST -H "Authorization: Bearer $GITHUB_TOKEN" \
                https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/statuses/$GIT_COMMIT_HASH \
                -d '{"state": "success", "context": "security/owasp", "description": "Scan Completed"}' > /dev/null
                '''
            }
        }

        stage('Reporting (PR Comment)') {
            when {
                expression { env.IS_PR == 'true' }
            }
            steps {
                sh '''
                TRIVY_VULNS=$(grep -o '"ruleId":' trivy-results.sarif 2>/dev/null | wc -l | tr -d ' ' || echo "0")
                GITLEAKS_LEAKS=$(grep -o '"Description":' gitleaks-report.json 2>/dev/null | wc -l | tr -d ' ' || echo "0")
                OWASP_VULNS=$(grep -o '"ruleId":' target/dependency-check-report.sarif 2>/dev/null | wc -l | tr -d ' ' || echo "0")

                cat <<EOF > pr_comment.json
{
  "body": "### 🛡️ DevSecOps Scan Summary\\n\\n**🔍 Gitleaks:** $GITLEAKS_LEAKS Secrets Detected\\n**🚨 Trivy:** $TRIVY_VULNS Vulnerabilities Found\\n**📦 OWASP:** $OWASP_VULNS Vulnerable Dependencies\\n\\n*Review inline annotations in the Files Changed tab.*"
}
EOF
                curl -s -X POST -H "Authorization: Bearer $GITHUB_TOKEN" \
                  -H "Accept: application/vnd.github.v3+json" \
                  https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/issues/$CHANGE_ID/comments \
                  -d @pr_comment.json > /dev/null
                '''
            }
        }
    }

    post {
        success {
            script {
                if (env.IS_PR == 'false' && env.BRANCH_NAME == 'main') {
                    sh """
                    TRIVY_VULNS=\$(grep -o '"ruleId":' trivy-results.sarif 2>/dev/null | wc -l | tr -d ' ' || echo "0")
                    GITLEAKS_LEAKS=\$(grep -o '"Description":' gitleaks-report.json 2>/dev/null | wc -l | tr -d ' ' || echo "0")
                    OWASP_VULNS=\$(grep -o '"ruleId":' target/dependency-check-report.sarif 2>/dev/null | wc -l | tr -d ' ' || echo "0")

                    curl -s -X POST -H 'Content-type: application/json' \
                    --data '{"text":"✅ *PR Merged to Main!* \\n*Repository:* '"${REPO_NAME}"' \\n*Commit:* '"${GIT_COMMIT_HASH}"' \\n\\n🛡️ *Final Security Scan Results:* \\n• *Gitleaks:* '"\$GITLEAKS_LEAKS"' Secrets \\n• *Trivy:* '"\$TRIVY_VULNS"' Vulns \\n• *OWASP:* '"\$OWASP_VULNS"' Vulns"}' \
                    ${SLACK_WEBHOOK}
                    """
                }
            }
        }
    }
}
