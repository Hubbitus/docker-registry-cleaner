package info.hubbitus

import com.fasterxml.jackson.annotation.JsonIgnore
import groovy.json.JsonSlurper
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import info.hubbitus.decision.KeepDecision
import org.apache.http.HttpResponse

import java.time.ZonedDateTime

/**
 * @author Pavel Alexeev.
 * @since 2018-02-19 01:37.
 */
@EqualsAndHashCode
@ToString(ignoreNulls=true, includeNames=true, includePackage=false)
class RegistryTagInfo {
	String application
	String name

	@Lazy
	ZonedDateTime created = {
		ZonedDateTime.parse(
			serviceRawInfo[RegistryClient.ApiSchemaVersion.V1].responseData.history[0].v1Compatibility.created)
	}()

	@Lazy
	KeepDecision keptBy = new KeepDecision()

	@JsonIgnore
	Map<RegistryClient.ApiSchemaVersion,HttpResponse> serviceRawInfo = [:]

	/**
	 * Empty constructor required for JSON [de]serialization
	 * It private and should not be used manually
	 */
	private RegistryTagInfo() { }

	RegistryTagInfo(String application, String name, RegistryClient.ApiSchemaVersion schemaVersion, rawServiceAnswer) {
		this.application = application
		this.name = name

		if (RegistryClient.ApiSchemaVersion.V1 == schemaVersion){
			rawServiceAnswer.responseData.history.each{
				it.v1Compatibility = new JsonSlurper().parseText(it.v1Compatibility)
			}
		}

		serviceRawInfo[schemaVersion] = rawServiceAnswer
	}
}
