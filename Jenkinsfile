
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
                    echo "=== TARGET: ${env.CHANGE_TARGET} ==="

                    checkout scm

                } else if (env.BRANCH_NAME) {
                    echo "=== AUTOMATIC BRANCH BUILD ==="
                    echo "=== BRANCH: ${env.BRANCH_NAME} ==="

                    checkout scm

                } else {
                    echo "=== MANUAL BUILD ==="
                    echo "=== BRANCH: ${params.TARGET_BRANCH} ==="

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

        stage('Approve Deploy') {
            when {
                expression {
                    return env.JOB_NAME == 'Vovus/BugPocket-Master/master'
                    && env.BRANCH_NAME == 'master'
                    && !env.CHANGE_ID
                }
            }

            steps {
                input message: 'Deploy BugPocket to production?',
                    ok: 'Deploy'
            }
        }

        stage('Deploy') {
            when {
                expression {
                    return env.JOB_NAME == 'Vovus/BugPocket-Master/master'
                    && env.BRANCH_NAME == 'master'
                    && !env.CHANGE_ID
                }
            }

            steps {
                sshagent(credential: ['SSH_CREDENTIAL_ID']) {
                    sh '''
                        set -eu
                        COMMIT_SHA="$(git rev-parse HEAD)"
                        ssh vovus@206.223.251.206 \
                        "bash" -selectedValue -- $COMMIT_SHA" <<'REMOTE'
                        set -eu

                        COMMIT_SHA="$1"

                        cd /home/vovus/apps/BugPocket

                        git fetch origin master
                        git checkout --detach "$COMMIT_SHA"

                        docker compose build app
                        docker compose up -d --no-deps app

                        sleep 30

                        curl -fsS http://127.0.0.1:8080/actuator/health
                        REMOTE

                    '''
                }
            }

        }

    }
}
