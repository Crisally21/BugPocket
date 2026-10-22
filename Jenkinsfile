
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
    }
}
