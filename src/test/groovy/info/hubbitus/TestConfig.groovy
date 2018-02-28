package info.hubbitus

/**
 * @author Pavel Alexeev.
 * @since 2017-09-17 21:24.
 */
interface TestConfig {
    String REPO_URL = 'https://docreg.taskdata.work/v2/'
    String REPO_LOGIN = System.env.REPO_LOGIN
    String REPO_PASSWORD = System.env.REPO_PASSWORD
}
