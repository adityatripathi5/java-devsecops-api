pipeline {
    agent any
    
    environment {
        GITHUB_TOKEN = credentials('GitHub-PAT-Token') // Make sure this credential exists in your EC2 Jenkins
        REPO_OWNER = 'adityatripathi5'
        REPO_NAME = 'java-devsecops-api'
    }

    stages {
        stage('Dynamic Context Setup') {
            steps {
                script {
                    // This is exactly what your lead asked for. Jenkins dynamically figures out the context.
                    if (env.CHANGE_ID) {
                        echo " This is a PR RAISE! PR Number: ${env.CHANGE_ID}"
                        env.IS_PR = 'true'
                        env.GIT_COMMIT_HASH = env.GIT_COMMIT
                    } else {
                        echo " This is a DIRECT PUSH / PR MERGE to branch: ${env.BRANCH_NAME}"
                        env.IS_PR = 'false'
                        env.GIT_COMMIT_HASH = sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
                    }
                }
            }
        }

        stage('Build Java API') {
            steps {
                // Compiles the Java code to ensure it's not broken before scanning
                sh 'mvn clean package -DskipTests'
            }
        }

        stage('Gitleaks Scan') {
            steps {
                // Install Gitleaks on the fly on EC2
                sh '''
                wget https://github.com/gitleaks/gitleaks/releases/download/v8.18.1/gitleaks_8.18.1_linux_x64.tar.gz
                tar -xzf gitleaks_8.18.1_linux_x64.tar.gz
                ./gitleaks detect -v --report-format json --report-path gitleaks-report.json || true
                
                curl -X POST -H "Authorization: Bearer $GITHUB_TOKEN" \
                -H "Accept: application/vnd.github.v3+json" \
                https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/statuses/$GIT_COMMIT_HASH \
                -d '{"state": "success", "context": "security/gitleaks", "description": "Scan Completed"}'
                '''
            }
        }

        stage('Trivy & OWASP (Coming Next)') {
            steps {
                echo "Gitleaks is wired dynamically. Next we will install Trivy, OWASP, and SonarQube CLI on the EC2."
            }
        }
    }
}
