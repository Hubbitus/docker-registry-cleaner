package info.hubbitus

import com.fasterxml.jackson.core.type.TypeReference
import org.junit.Rule
import org.springframework.boot.test.rule.OutputCapture
import spock.lang.Shared
import spock.lang.Specification

import java.time.Clock
import java.time.ZonedDateTime

/**
 * @author Pavel Alexeev.
 * @since 2018-03-04 12:25.
 */
class RegistryCleanerTest extends Specification {
	// We need define outer client to break cycle dep for cleaner <-> JSONtestTags (last use cleaner for obtain mapper)
	private RegistryCleaner _cleaner = new RegistryCleaner('@' + getClass().getResource('/test.args').file)

	static final private ZonedDateTime TEST_INVOCATION_TIME = ZonedDateTime.parse('2018-03-02T23:24:25+03:00[Europe/Moscow]')

	@Lazy
	RegistryCleaner cleaner = {
		RegistryClient client = Mock()
		client.getCatalog() >> { JSONtestTags.keySet().toList() }
		client.getApplicationTagsDetails(_) >> {String application ->
			JSONtestTags[application]
		}
		_cleaner.client = client
		// Does not want full overhead of mocking. And also Spy is discouraged in Spock See https://stackoverflow.com/questions/2001671/override-java-system-currenttimemillis-for-testing-time-sensitive-code/2001728
		_cleaner.clock = Clock.fixed(TEST_INVOCATION_TIME.toInstant(), TEST_INVOCATION_TIME.zone)
		_cleaner
	}()

	@Lazy
	HashMap<String,List<RegistryTagInfo>> JSONtestTags = {
		TypeReference<HashMap<String,List<RegistryTagInfo>>> typeRef = new TypeReference<HashMap<String,List<RegistryTagInfo>>>() {}; // By https://stackoverflow.com/questions/2525042/how-to-convert-a-json-string-to-a-mapstring-string-with-jackson-json/2525152#2525152
			// Note! In Idea interactive evaluate it also work as .getResource('registry-data.test.json'), without /, but not in test! And getClass().getResource('.') mapped to something like `file:/home/pasha/@Projects/_OPEN/docker-registry-cleaner/out/test/classes/info/hubbitus/`
		_cleaner.jsonMapper.readValue(getClass().getResource('/registry-data.test.json').text, typeRef)
	}()

	@Shared List expectedAllApplications = ['art', 'addressmaster', 'bp-app']
	@Shared List expectedFilteredSetOfApplications = ['art', 'bp-app'] // according to --only-application option in test.args file

	def 'testReadTestData (self-test)'(){
		expect:
			JSONtestTags instanceof Map
			JSONtestTags.size() == 3
			JSONtestTags.keySet() == ['art', 'addressmaster', 'bp-app'] as Set
	}

	def 'simple Mock test'(){
		given:
			RegistryCleaner directTestCleaner = new RegistryCleaner('@' + getClass().getResource('/test.args').file)

			RegistryClient directTestClient = Mock()
			directTestClient.getCatalog() >> JSONtestTags.keySet().toList()
			directTestClient.getApplicationTagsDetails(_) >> {String application ->
				JSONtestTags[application]
			}

			directTestCleaner.client = directTestClient

		when: 'Check client basic Mock'
			List<String> applications = directTestClient.getCatalog()
		then:
			applications instanceof List
			applications.size() == expectedAllApplications.size()
			applications.sort() == expectedAllApplications.sort()

		when: 'Check basic cleaner with mocked client (created in method)'
			Map<String, List<RegistryTagInfo>> listOfTags = directTestCleaner.getTagsDetailsByApplications()

		then:
			listOfTags.size() == expectedFilteredSetOfApplications.size()
			listOfTags.keySet() == expectedFilteredSetOfApplications as Set

		when: 'Check basic cleaner with mocked client (created in class lazily)'
			listOfTags = cleaner.getTagsDetailsByApplications()

		then:
			listOfTags.size() == expectedFilteredSetOfApplications.size()
			listOfTags.keySet() == expectedFilteredSetOfApplications as Set

		when:'Try to mock tim,e globaly - initially'
			true
		then:
			ZonedDateTime.now() != ZonedDateTime.parse("2018-01-02T23:24:25+03:00[Europe/Moscow]")

		when:'Fix time returned by ZonedDateTime.now()'
		then:
			cleaner.now() == ZonedDateTime.parse("2018-03-02T23:24:25+03:00[Europe/Moscow]")
	}

	def 'prepareListOfTagsToClean'() {
		when:
			Map<String, List<RegistryTagInfo>> res = cleaner.prepareListOfTagsToClean()
		then:
			res
			res.size() == expectedFilteredSetOfApplications.size()
			res.keySet() == expectedFilteredSetOfApplications as Set
			res.art.size() == 2
			res.art.each {
				assert it.application == 'art'
				assert !it.keptBy.isForDelete()
				assert it.keptBy.keepTagOption.parent.name == 'GLOBAL'
			}
			res.'bp-app'.size() == 130

			// 'bp-app: dev and master exact matches should stay':
			res.'bp-app'.findAll{ it.name in ['dev', 'master'] }.each {
				assert !it.keptBy.isForDelete()
				assert it.keptBy.keepTagOption.parent.name == 'bp-app'
				assert it.keptBy.keepTagOption.tagRegexp == '^(dev|master)$'
				assert it.keptBy.keepByTop
				assert !it.keptBy.keepByPeriod
			}
			res.'bp-app'.findAll{ it.keptBy.keepByPeriod && 'GLOBAL' == it.keptBy.keepTagOption.parent.name }.collect{ it.name } as Set == ['RR-414-3b09a37', 'RR-156_EV_due_date_control-769d345', 'RR-156_EV_due_date_control-e3d3506', 'RR-441-SIA_2-6109e2c', 'RR-156_EV_due_date_control-41f63f2', 'RR-414-68887c6', 'RR-414-f987bf0', '0.1.3-400d522'] as Set
			res.'bp-app'.findAll{ it.keptBy.keepByPeriod && 'bp-app' == it.keptBy.keepTagOption.parent.name }.collect{ it.name } as Set == ['master-b8b3823', 'master-81bd0ad', 'master-b00e98d', 'master-400d522'] as Set
	}

	/**
	 * Capture System.out.println calls!
	 * @link http://mrhaki.blogspot.ru/2015/02/spocklight-capture-and-assert-system.html
	 */
	@Rule
	OutputCapture capture = new OutputCapture()

	def 'printListTags'() {
		when:
			cleaner.clean()

		then:
			capture.toString().trim() == '''Tag=[art:3.2      ]; time=[2018-02-20T14:04:54.951994741Z[UTC]]; isForDelete=[false]; [KeepDecision(keepTagOption:KeepOption$KeepTagOption(parent:KeepOption(GLOBAL), tagRegexp:.*, top:5, period:604800, periodStr:1w), keepByTop:true, keepByPeriod:false)]
Tag=[art:3.0-beta3]; time=[2018-02-20T20:22:18.593278905Z[UTC]]; isForDelete=[false]; [KeepDecision(keepTagOption:KeepOption$KeepTagOption(parent:KeepOption(GLOBAL), tagRegexp:.*, top:5, period:604800, periodStr:1w), keepByTop:true, keepByPeriod:false)]
Tag=[bp-app:dev-fe77a06                              ]; time=[2018-01-24T13:27:28.463593716Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:master-3d78f31                           ]; time=[2018-02-14T13:34:26.417557389Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:fix-index-master-f120510                 ]; time=[2018-02-05T07:29:28.547081017Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:dev-692a89c                              ]; time=[2018-02-05T09:22:02.091675529Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:dev-5525b4a                              ]; time=[2018-02-05T12:37:51.913028654Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:RR-137-4aa15f1                           ]; time=[2018-02-11T00:54:19.826086077Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:RR-137-f9cd2da                           ]; time=[2018-02-11T23:03:29.789979902Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:RR-137-78909c9                           ]; time=[2018-02-11T23:17:20.728198209Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:dev-f29e6a6                              ]; time=[2018-02-13T09:04:01.554640731Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:dev-447820d                              ]; time=[2018-02-13T09:14:53.281537159Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:dev-49726c1                              ]; time=[2018-02-13T11:27:29.424239251Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:RR-163-19a1ed5                           ]; time=[2018-02-13T11:44:27.182739119Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:RR-163-5ec5b28                           ]; time=[2018-02-13T11:45:18.070481264Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:dev-e1e1eaa                              ]; time=[2018-02-13T12:35:44.754077601Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:PSKO-1125-3e93b5a                        ]; time=[2018-02-13T11:56:39.780221934Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:RR-107_fix_date_format-70fc9b0           ]; time=[2018-02-13T12:38:47.834117845Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:dev-27dd3bc                              ]; time=[2018-02-13T12:40:06.379061339Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:RR-107_fix_date_format-1318e07           ]; time=[2018-02-13T12:52:34.852940377Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:dev-c01fdf9                              ]; time=[2018-02-13T12:54:17.227322789Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:RR-163-160755f                           ]; time=[2018-02-13T12:56:33.708714345Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:RR-107_fix_date_format-f7d1ffe           ]; time=[2018-02-13T12:58:19.240029393Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:1125-689299b                             ]; time=[2018-02-13T14:00:18.042913135Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:dev-6c33f6c                              ]; time=[2018-02-13T14:33:58.867645061Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:dev-0335b05                              ]; time=[2018-02-13T14:36:23.594984553Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:RR-107_fix_date_format-7d0d81a           ]; time=[2018-02-13T14:38:15.176563851Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:RR-207-CRUD_USER-2-0ba775a               ]; time=[2018-02-13T14:38:47.542344524Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:dev-a7c97c9                              ]; time=[2018-02-13T16:58:45.652982824Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:RR-107_update_dto-301f03f                ]; time=[2018-02-14T08:58:16.142562138Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:dev-689edc1                              ]; time=[2018-02-14T10:34:34.681170063Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:RR-207-CRUD_USER-2-cb2e551               ]; time=[2018-02-14T12:17:50.380789857Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:RR-107_handle_not_well_formed_doc-4589510]; time=[2018-02-14T12:29:00.029497729Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:dev-7a5cce5                              ]; time=[2018-02-14T12:32:15.993550519Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:RR-291-drop-process-88eb773              ]; time=[2018-02-14T12:43:41.994702628Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:RR-222-fix_process_page-d0c101c          ]; time=[2018-02-14T12:56:25.328841439Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:dev-e92693c                              ]; time=[2018-02-14T13:29:55.443466343Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:dev-4ff1ace                              ]; time=[2018-02-14T13:57:47.630757473Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:master-4ff1ace                           ]; time=[2018-02-14T13:39:13.664920264Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:RR-172-aa86c32                           ]; time=[2018-02-15T08:01:00.594848887Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:fix-index-master-12b1543                 ]; time=[2018-02-15T09:14:29.511712595Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:dev-75dc83b                              ]; time=[2018-02-15T09:16:39.348942798Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:RR-222-fix_process_page-75dc83b          ]; time=[2018-02-15T09:19:54.878014335Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:fix-index-master-d9ad6fa                 ]; time=[2018-02-15T09:19:57.503797057Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:dev-7fdbc64                              ]; time=[2018-02-15T09:24:03.400624506Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:RR-172-2e049a8                           ]; time=[2018-02-15T09:41:13.867097258Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:RR-172-207b0f8                           ]; time=[2018-02-15T10:52:51.214047359Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:RR-172-42aac14                           ]; time=[2018-02-16T06:38:26.126034385Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:RR-251-multypart-uploader-f6ffa77        ]; time=[2018-02-16T10:28:01.723247342Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:dev-9386e2d                              ]; time=[2018-02-16T14:10:28.524350754Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:master-9386e2d                           ]; time=[2018-02-16T15:48:16.379373363Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:master-9415bf4                           ]; time=[2018-02-16T15:28:17.846059912Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:RR-251-multypart-uploader-341fff6        ]; time=[2018-02-19T09:54:12.248995893Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:dev-09fa85c                              ]; time=[2018-02-19T09:57:08.148024201Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:dev-ff22016                              ]; time=[2018-02-19T11:48:13.246364346Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:dev-ec2e43b                              ]; time=[2018-02-19T11:59:43.735767313Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:dev-96af953                              ]; time=[2018-02-19T12:25:05.289257846Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:dev-03033fd                              ]; time=[2018-02-19T13:44:51.319255384Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:dev-9707484                              ]; time=[2018-02-19T13:49:19.913175255Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:dev-98c0062                              ]; time=[2018-02-19T13:54:09.812913101Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:RR-370-add-file-9ba4f75                  ]; time=[2018-02-19T14:06:59.699184106Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:RR-370-add-file-f722d0d                  ]; time=[2018-02-19T14:11:51.105381408Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:dev-fcc9e0e                              ]; time=[2018-02-19T15:09:38.985263377Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:dev-6006557                              ]; time=[2018-02-19T15:11:28.611289140Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:master-6006557                           ]; time=[2018-02-19T15:46:23.419765556Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:RR-134-76ddaef                           ]; time=[2018-02-19T21:37:32.761438500Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:master-90f594e                           ]; time=[2018-02-20T08:28:03.975305112Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:master-8e2d676                           ]; time=[2018-02-20T09:07:10.952397448Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:master-130f230                           ]; time=[2018-02-20T09:34:13.972580443Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:dev-130f230                              ]; time=[2018-02-20T09:44:06.714767885Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:master-1e98084                           ]; time=[2018-02-20T10:04:03.071535573Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:master-3584615                           ]; time=[2018-02-20T10:11:48.902572844Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:master-7b5e871                           ]; time=[2018-02-20T11:16:20.833140129Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:master-106cda0                           ]; time=[2018-02-20T12:14:27.389671628Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:master-b5f5a6f                           ]; time=[2018-02-20T13:08:02.957122579Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:master-ae2613d                           ]; time=[2018-02-20T13:29:28.240913152Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:master-d1f28f4                           ]; time=[2018-02-20T13:59:31.919869507Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:master-b97d738                           ]; time=[2018-02-20T14:18:05.847343018Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:master-f756a06                           ]; time=[2018-02-20T15:13:50.178782073Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:master-b5a76e0                           ]; time=[2018-02-20T15:22:58.123361268Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:master_clean-c6388ed                     ]; time=[2018-02-20T15:48:40.317362840Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:master-531cd0e                           ]; time=[2018-02-20T16:05:56.286744294Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:master-a4f86c7                           ]; time=[2018-02-20T16:16:57.263976680Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:master-17912bc                           ]; time=[2018-02-21T07:05:26.342919657Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:master-9db8f9c                           ]; time=[2018-02-21T07:16:33.692835906Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:master-4385fbd                           ]; time=[2018-02-21T07:44:32.900472084Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:master-4e964ee                           ]; time=[2018-02-21T08:10:21.030578159Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:master-f5013da                           ]; time=[2018-02-21T08:32:35.546759773Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:master-7a0e595                           ]; time=[2018-02-21T09:26:43.651567231Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:master-e6ae502                           ]; time=[2018-02-21T09:56:28.810553246Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:master-6873c12                           ]; time=[2018-02-21T10:28:26.755951531Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:master-8fa4258                           ]; time=[2018-02-21T10:50:27.619879684Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:dev-8fa4258                              ]; time=[2018-02-21T11:55:48.317860192Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:master-38de4f3                           ]; time=[2018-02-21T13:58:33.340199917Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:master-5c55141                           ]; time=[2018-02-21T14:23:31.409614393Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:master-e9e0092                           ]; time=[2018-02-21T14:23:37.575899883Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:master-bc0ad2e                           ]; time=[2018-02-21T14:25:54.878486661Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:master-5330076                           ]; time=[2018-02-21T15:11:52.804264219Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:master-bd20cae                           ]; time=[2018-02-21T15:23:42.358866083Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:dev-bd20cae                              ]; time=[2018-02-22T09:20:59.570433837Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:dev-345c05a                              ]; time=[2018-02-22T09:36:06.811250821Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:RR-172-f25448b                           ]; time=[2018-02-22T11:23:48.874959628Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:0.1.2-bd20cae                            ]; time=[2018-02-22T11:23:55.286179527Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:0.1.1-4ff1ace                            ]; time=[2018-02-22T11:24:00.439266987Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:dev-0433403                              ]; time=[2018-02-22T11:30:59.602702785Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:dev-1197366                              ]; time=[2018-02-22T12:41:55.441006331Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:dev-8c45bf6                              ]; time=[2018-02-26T16:07:02.477490330Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:RR-369-3290f40                           ]; time=[2018-02-22T16:41:15.713675223Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:RR-137-e06fb0a                           ]; time=[2018-02-23T00:04:35.962233791Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:dev-9cca46d                              ]; time=[2018-02-27T09:00:24.618616427Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:dev-e797c61                              ]; time=[2018-02-27T13:10:02.017539853Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:dev-dabcd62                              ]; time=[2018-02-27T15:36:42.628136379Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:dev-94c1ba5                              ]; time=[2018-02-27T15:41:38.138477235Z[UTC]]; isForDelete=[true ]; [KeepDecision(keepTagOption:null, keepByTop:false, keepByPeriod:false)]
Tag=[bp-app:RR-414-3b09a37                           ]; time=[2018-02-25T01:44:16.262318615Z[UTC]]; isForDelete=[false]; [KeepDecision(keepTagOption:KeepOption$KeepTagOption(parent:KeepOption(GLOBAL), tagRegexp:.*, top:5, period:604800, periodStr:1w), keepByTop:false, keepByPeriod:true)]
Tag=[bp-app:RR-156_EV_due_date_control-769d345       ]; time=[2018-02-27T14:48:08.836808315Z[UTC]]; isForDelete=[false]; [KeepDecision(keepTagOption:KeepOption$KeepTagOption(parent:KeepOption(GLOBAL), tagRegexp:.*, top:5, period:604800, periodStr:1w), keepByTop:false, keepByPeriod:true)]
Tag=[bp-app:RR-156_EV_due_date_control-e3d3506       ]; time=[2018-02-27T17:16:49.662866941Z[UTC]]; isForDelete=[false]; [KeepDecision(keepTagOption:KeepOption$KeepTagOption(parent:KeepOption(GLOBAL), tagRegexp:.*, top:5, period:604800, periodStr:1w), keepByTop:false, keepByPeriod:true)]
Tag=[bp-app:RR-441-SIA_2-6109e2c                     ]; time=[2018-02-28T07:39:21.023391353Z[UTC]]; isForDelete=[false]; [KeepDecision(keepTagOption:KeepOption$KeepTagOption(parent:KeepOption(GLOBAL), tagRegexp:.*, top:5, period:604800, periodStr:1w), keepByTop:true, keepByPeriod:true)]
Tag=[bp-app:RR-156_EV_due_date_control-41f63f2       ]; time=[2018-02-28T12:55:02.707215949Z[UTC]]; isForDelete=[false]; [KeepDecision(keepTagOption:KeepOption$KeepTagOption(parent:KeepOption(GLOBAL), tagRegexp:.*, top:5, period:604800, periodStr:1w), keepByTop:true, keepByPeriod:true)]
Tag=[bp-app:RR-414-68887c6                           ]; time=[2018-02-28T13:27:42.441952270Z[UTC]]; isForDelete=[false]; [KeepDecision(keepTagOption:KeepOption$KeepTagOption(parent:KeepOption(GLOBAL), tagRegexp:.*, top:5, period:604800, periodStr:1w), keepByTop:true, keepByPeriod:true)]
Tag=[bp-app:RR-414-f987bf0                           ]; time=[2018-02-28T13:42:42.692044426Z[UTC]]; isForDelete=[false]; [KeepDecision(keepTagOption:KeepOption$KeepTagOption(parent:KeepOption(GLOBAL), tagRegexp:.*, top:5, period:604800, periodStr:1w), keepByTop:true, keepByPeriod:true)]
Tag=[bp-app:0.1.3-400d522                            ]; time=[2018-03-02T12:24:13.442968508Z[UTC]]; isForDelete=[false]; [KeepDecision(keepTagOption:KeepOption$KeepTagOption(parent:KeepOption(GLOBAL), tagRegexp:.*, top:5, period:604800, periodStr:1w), keepByTop:true, keepByPeriod:true)]
Tag=[bp-app:master                                   ]; time=[2018-03-01T07:48:42.467459196Z[UTC]]; isForDelete=[false]; [KeepDecision(keepTagOption:KeepOption$KeepTagOption(parent:KeepOption(bp-app), tagRegexp:^(dev|master)$, top:2), keepByTop:true, keepByPeriod:false)]
Tag=[bp-app:dev                                      ]; time=[2018-03-02T10:23:15.754942986Z[UTC]]; isForDelete=[false]; [KeepDecision(keepTagOption:KeepOption$KeepTagOption(parent:KeepOption(bp-app), tagRegexp:^(dev|master)$, top:2), keepByTop:true, keepByPeriod:false)]
Tag=[bp-app:dev-588e886                              ]; time=[2018-02-27T15:48:39.036452851Z[UTC]]; isForDelete=[false]; [KeepDecision(keepTagOption:KeepOption$KeepTagOption(parent:KeepOption(bp-app), tagRegexp:^dev-, top:5), keepByTop:true, keepByPeriod:false)]
Tag=[bp-app:dev-877e771                              ]; time=[2018-02-28T07:11:49.883955591Z[UTC]]; isForDelete=[false]; [KeepDecision(keepTagOption:KeepOption$KeepTagOption(parent:KeepOption(bp-app), tagRegexp:^dev-, top:5), keepByTop:true, keepByPeriod:false)]
Tag=[bp-app:dev-634c014                              ]; time=[2018-02-28T07:39:53.730091859Z[UTC]]; isForDelete=[false]; [KeepDecision(keepTagOption:KeepOption$KeepTagOption(parent:KeepOption(bp-app), tagRegexp:^dev-, top:5), keepByTop:true, keepByPeriod:false)]
Tag=[bp-app:dev-92a66b5                              ]; time=[2018-02-28T14:31:24.896067239Z[UTC]]; isForDelete=[false]; [KeepDecision(keepTagOption:KeepOption$KeepTagOption(parent:KeepOption(bp-app), tagRegexp:^dev-, top:5), keepByTop:true, keepByPeriod:false)]
Tag=[bp-app:dev-9fb6481                              ]; time=[2018-03-02T10:23:15.754942986Z[UTC]]; isForDelete=[false]; [KeepDecision(keepTagOption:KeepOption$KeepTagOption(parent:KeepOption(bp-app), tagRegexp:^dev-, top:5), keepByTop:true, keepByPeriod:false)]
Tag=[bp-app:master-b8b3823                           ]; time=[2018-02-27T11:41:18.540018206Z[UTC]]; isForDelete=[false]; [KeepDecision(keepTagOption:KeepOption$KeepTagOption(parent:KeepOption(bp-app), tagRegexp:^master-, period:345600, periodStr:4d), keepByTop:false, keepByPeriod:true)]
Tag=[bp-app:master-81bd0ad                           ]; time=[2018-02-27T14:22:16.606340182Z[UTC]]; isForDelete=[false]; [KeepDecision(keepTagOption:KeepOption$KeepTagOption(parent:KeepOption(bp-app), tagRegexp:^master-, period:345600, periodStr:4d), keepByTop:false, keepByPeriod:true)]
Tag=[bp-app:master-b00e98d                           ]; time=[2018-02-27T14:54:18.414942986Z[UTC]]; isForDelete=[false]; [KeepDecision(keepTagOption:KeepOption$KeepTagOption(parent:KeepOption(bp-app), tagRegexp:^master-, period:345600, periodStr:4d), keepByTop:false, keepByPeriod:true)]
Tag=[bp-app:master-400d522                           ]; time=[2018-03-01T07:48:42.467459196Z[UTC]]; isForDelete=[false]; [KeepDecision(keepTagOption:KeepOption$KeepTagOption(parent:KeepOption(bp-app), tagRegexp:^master-, period:345600, periodStr:4d), keepByTop:false, keepByPeriod:true)]'''
	}
}
