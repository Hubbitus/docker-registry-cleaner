package info.hubbitus

import spock.lang.Shared
import spock.lang.Specification

import java.time.ZonedDateTime

import static info.hubbitus.TestConfig.*

/**
 * This is not "honest" test. I think it is not easy mock Docker registry API, so it against one existing instance
 * To run it you must define env variables REPO_LOGIN, REPO_PASSWORD
 * @see TestConfig
 *
 * @author Pavel Alexeev.
 * @since 2017-09-17 21:22.
 */
class RegistryClientTest extends Specification {
    @Shared RegistryClient client = new RegistryClient(REPO_URL, REPO_LOGIN, REPO_PASSWORD)

    static final String APP_NAME = 'egaisapp'

    def "test getCatalog"() {
        when:
            List<String> apps = client.getCatalog()
        then:
            apps
            apps.size() > 1
            apps.find{APP_NAME}
    }

    def "test getTags"() {
        when:
            List<String> tags = client.getTags(APP_NAME)
        then:
            tags
            tags.size() > 1
    }

    def "test getTagInfo [#tag]"() {
        when:
            RegistryTagInfo tagInfo = client.getTagInfo(APP_NAME, tag)
        then:
            tagInfo
        where:
            tag << client.getTags(APP_NAME).take(10)
    }

    def "test getApplicationWithTagDetails"() {
        when:
            Map ret = client.getApplicationTagsDetails(APP_NAME)
        then:
            ret
            ret.size() > 1
    }

    def "test getTagsWithBuildDates"() {
        when:
            Map<String, ZonedDateTime> res = client.getTagsWithBuildDates(APP_NAME)
        then:
            res
            res.size() > 1
    }
}
