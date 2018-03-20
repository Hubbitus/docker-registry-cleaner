package info.hubbitus

import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

import static info.hubbitus.TestConfig.*

/**
 * This is not "honest" test. I think it is not easy mock Docker registry API, so it against one existing instance
 * To run it you must define env variables REPO_LOGIN, REPO_PASSWORD
 * @see TestConfig
 *
 * @author Pavel Alexeev.
 * @since 2017-09-17 21:22.
 */
@Ignore('This is real integration test and require ENV variables with user/pass to access to registry. See RegistryCleanerTest to test including client with mocks')
class RegistryClientTest extends Specification {
	@Shared RegistryClient client = new RegistryClient(REPO_URL, REPO_LOGIN, REPO_PASSWORD)

	static final String APP_NAME = 'egaisapp'

	def "getCatalog"() {
		when:
			List<String> apps = client.getCatalog()
		then:
			apps
			apps.size() > 1
			apps.find{APP_NAME}
	}

	def "getTags"() {
		when:
			List<String> tags = client.getTags(APP_NAME)
		then:
			tags
			tags.size() > 1
	}

	def "getTagInfo [#tag]"() {
		when:
			RegistryTagInfo tagInfo = client.getTagInfo(APP_NAME, tag)
		then:
			tagInfo
		where:
			tag << client.getTags(APP_NAME).take(10)
	}

	def "getApplicationWithTagDetails"() {
		when:
			List<RegistryTagInfo> ret = client.getApplicationTagsDetails(APP_NAME)
		then:
			ret
			ret.size() > 1
	}
}
