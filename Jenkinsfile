
pipeline {
    agent {
        label 'agent-1'
    }

    options {
        skipDefaultCheckout()

        buildDiscarder(logRotator(
            numToKeepStr: '10',
            artifactNumToKeepStr: '2'
        ))
    }

    parameters {
        gitParameter(
            name: 'TARGET_BRANCH',
            type: 'PT_BRANCH',
            branchFilter: 'origin/(.*)',
            defaultValue: 'ci/jenkins',
            description: 'Choose Git branch to build',
            selectedValue: 'DEFAULT',
            sortMode: 'ASCENDING_SMART',
            quickFilterEnabled: false,
            listSize: '1'
        )
    }

    stages {



        stage('Checkout') {
            steps {
                script {
                    if (env.CHANGE_ID) {
                        echo "=== PULL REQUEST #${env.CHANGE_ID} ==="
                        echo "=== TARGET BRANCH: ${env.CHANGE_TARGET} ==="

                        checkout scm
                    } else {
                        echo "=== MANUAL BUILD: ${params.TARGET_BRANCH} ==="

                        git branch: params.TARGET_BRANCH,
                            url: 'https://github.com/Crisally21/BugPocket.git'
                    }
                }

                sh '''
                    echo "=== CHECKED OUT COMMIT ==="
                    git log -1 --oneline
                    git rev-parse HEAD
                '''
            }
        }



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

        stage('Frontend Tests') {
            steps {
                echo '=== INSTALLING FRONTEND DEPENDENCIES ==='

                sh 'npm ci'

                echo '=== CHECKING JAVASCRIPT SYNTAX ==='

                sh 'npm run check:ui'

                echo '=== RUNNING FRONTEND TESTS ==='

                sh 'npm run test:ui'

                echo '=== FRONTEND TESTS COMPLETED ==='
            }
        }

        stage('Package') {
            steps {
                echo '=== BUILDING SPRING BOOT JAR ==='

                sh 'mvn -B -ntp -DskipTests package'

                echo '=== CHECKING GENERATED JAR ==='

                sh 'ls -lh target/*.jar'
            }

            post {
                success {
                    echo '=== ARCHIVING JAR ==='

                    archiveArtifacts artifacts: 'target/*.jar',
                        fingerprint: true
                }
            }
        }
    }
}
