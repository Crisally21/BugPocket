
pipeline {
    agent {
        label 'agent-1'
    }

    stages {
        stage('Initialization') {
            steps {
                echo '=== BUGPOCKET CI STARTED ==='

                sh '''
                    echo "Working directory: $(pwd)"
                    echo "Commit: $(git rev-parse --short HEAD)"

                    java -version
                    mvn -version
                    node --version
                    npm --version
                '''
            }
        }

        stage('Backend Tests') {
            steps {
                echo '=== RUNNING BACKEND TESTS ==='

                sh 'mvn -B -ntp clean test'

                echo '=== BACKEND TESTS COMPLETED ==='
            }

            post {
                always {
                    echo '=== PUBLISHING JUNIT RESULTS ==='

                    junit 'target/surefire-reports/TEST-*.xml'
                }
            }
        }
    }
}
