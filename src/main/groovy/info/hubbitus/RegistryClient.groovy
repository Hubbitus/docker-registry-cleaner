package info.hubbitus

import groovy.json.JsonSlurper
import groovy.transform.Memoized
import groovy.util.logging.Slf4j
import groovyx.gpars.GParsPool
import groovyx.net.http.ContentType
import groovyx.net.http.HttpResponseException
import groovyx.net.http.RESTClient
import info.hubbitus.exceptions.TagNotFoundException
import info.hubbitus.utils.bench.ProgressLogger

import java.time.ZonedDateTime

import static org.apache.http.HttpStatus.SC_NOT_FOUND

/**
 * @author Pavel Alexeev.
 * @since 2017-09-16 13:35.
 */
@Slf4j
class RegistryClient {
	String url;
	String user;
	String password;

	private final static int CONCURRENT_REST_CALLS = 10

	/**
	 * Api schema media types
	 * @link https://docs.docker.com/registry/spec/manifest-v2-2/#media-types
	 *
	 * Unfortunately there very interesting behaviour:
	 *  * Information by tagRegexp in V1 (old) format contains history, and we can use it to extract build date.
	 *    But received in response header Docker-Content-Digest value is *not* accepted for tagRegexp deletion.
	 *  * For obtain such manifest SHA256 hash we must re-request such info from server again, with V2 accept header!
	 * Looks like some incompatability.
	 */
	static enum ApiSchemaVersion {
		V1('application/vnd.docker.distribution.manifest.v1+json'),
		V2('application/vnd.docker.distribution.manifest.v2+json')

		String acceptHeader

		ApiSchemaVersion(String acceptHeader) {
			this.acceptHeader = acceptHeader
		}
	}

	RegistryClient(String url, String user, String password) {
		this.url = url
		this.user = user
		this.password = password
	}

	/**
	 * RESTClient does not seams Thread safety. So use ThreadLocal instances
	 */
	ThreadLocal<RESTClient> restClient = ThreadLocal.withInitial{
		RESTClient restClient = new RESTClient(url, ContentType.JSON)
		restClient.auth.basic(user, password)
		return restClient
	} as ThreadLocal<RESTClient>

	/**
	 * Return list of applications (catalog)
	 *
	 * @link https://docreg.taskdata.work/v2/egaisapp/tags/list
	 * @return
	 */
	List<String> getCatalog(){
		return restClient.get().get(path: '_catalog').responseData.repositories
	}

	/**
	 * @link https://docreg.taskdata.work/v2/egaisapp/tags/list
	 *
	 * @param application
	 * @return optionsByTag list by application name
	 */
	List<String> getTags(String application){
		try{
			def resp = restClient.get().get(path: "$application/tags/list")

			return resp.responseData.tags
		}
		catch (HttpResponseException e){
			if (404 == e.statusCode){
				return []
			}else{
				throw e
			}
		}
	}

	/**
	 * Return manifest
	 *
	 * @param application name of application
	 * @param tag
	 * @param schemaVersion {@see ApiSchemaVersion} description
	 * @return https://docs.docker.com/registry/spec/manifest-v2-2/#image-manifest-field-descriptions
	 */
	RegistryTagInfo getTagInfo(String application, String tag, ApiSchemaVersion schemaVersion = ApiSchemaVersion.V1){
		try{
			def resp = restClient.get().get(
				path: "$application/manifests/$tag"
				// Header required: https://stackoverflow.com/questions/37033055/how-can-i-use-the-docker-registry-api-v2-to-delete-an-image-from-a-private-regis/37040883#37040883
				,headers: [Accept: schemaVersion.getAcceptHeader()]
			)
			return new RegistryTagInfo(application, tag, schemaVersion, resp)
		}
		catch (HttpResponseException e){
			if (SC_NOT_FOUND == e.statusCode){
				log.warn("Tag [$tag] not found")
				throw new TagNotFoundException("Tag [$tag] not found")
			}
			else throw e
		}
	}

	/**
	 * Request list of RegistryTagInfo with filled tagRegexp information
	 *
	 * @param application name of application
	 * @return list of RegistryTagInfo
	 */
	@Memoized
	List<RegistryTagInfo> getApplicationTagsDetails(String application){
		GParsPool.withPool(CONCURRENT_REST_CALLS) {
			List<String> tags = getTags(application)
			ProgressLogger pl = new ProgressLogger(tags, log.&info, application)
			return tags.collectParallel { tag ->
				pl.next{
					try{
						getTagInfo(application, tag)
					}
					catch (Throwable t){
						log.error("Exception happened on get tag info:", t)
						throw t;
					}
				}
			}
		} as List<RegistryTagInfo>
	}

	/**
	 * @link https://docs.docker.com/registry/spec/api/#deleting-an-image
	 * @link https://stackoverflow.com/questions/37033055/how-can-i-use-the-docker-registry-api-v2-to-delete-an-image-from-a-private-regis
	 *
	 * Unfortunately on deletion we MUST re-request tagRegexp info to get correct SHA256 hash. {@see ApiSchemaVersion}
	 *
	 * @param tag
	 * @return
	 */
	def deleteTag(RegistryTagInfo tag){
		try{
			RegistryTagInfo tagInfoV2 = getTagInfo(tag.application, tag.name, ApiSchemaVersion.V2)
			restClient.get().delete(path: "${tag.application}/manifests/${tagInfoV2.serviceRawInfo[ApiSchemaVersion.V2].responseBase.headergroup.getHeaders('Docker-Content-Digest')[0].getValue()}")
			log.info("Tag [$tag] deleted!")
		}
		catch (TagNotFoundException e){
			log.warn("Strange, but tag [$tag] not found on deletion! Skipping")
		}
	}
}
