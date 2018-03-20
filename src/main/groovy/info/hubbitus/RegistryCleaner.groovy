package info.hubbitus

import groovy.text.SimpleTemplateEngine
import groovy.text.Template
import groovy.util.logging.Slf4j
import groovyx.gpars.GParsPool
import info.hubbitus.cli.CliOptions
import info.hubbitus.cli.JCommanderAutoWidth
import info.hubbitus.cli.KeepOption
import info.hubbitus.cli.KeepOption.KeepTagOption

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.util.ISO8601DateFormat
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule

import java.time.Clock
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * @author Pavel Alexeev.
 * @since 2017-09-18 03:59.
 */
@Slf4j
class RegistryCleaner {
	/**
	 * Amount of concurrent threads for request remote data from registry and process it
	 */
	private final static int CONCURRENT_APPLICATION_PROCESSING = 5

	private RegistryClient client

	final private CliOptions options

	RegistryCleaner(String[] args) {
		options = new CliOptions()
		JCommanderAutoWidth jCommander = new JCommanderAutoWidth(options, args)
		if (options.help) {
			jCommander.usage()
			return;
		}
		options.postValidate()

		client = new RegistryClient(options.registryURL, options.login, options.password)
	}

	/**
	 * Main clean logic
	 */
	void clean() {
		Map<String, List<RegistryTagInfo>> tagList = prepareListOfTagsToClean()

//??            if (!options.delete){
//                toDel.each{app->
//                    app.
//                }
//            }

		printListTags(tagList)
	}

	/**
	 * Printout results of marking tags for deletion
	 *
	 * @param tagList Map of Application name -> list of tags
	 * @return
	 */
	def printListTags(Map<String, List<RegistryTagInfo>> tagsByApp){
		Template tpl = new SimpleTemplateEngine().createTemplate(options.format)

		tagsByApp.each {app, tags->
			tags.sort{a, b->
				a.keptBy?.keepTagOption?.tagRegexp <=> b.keptBy?.keepTagOption?.tagRegexp ?: b.keptBy.isForDelete() <=> a.keptBy.isForDelete()
			}.each{tag->
				println tpl.make(tag: tag, tags: tags)
			}
		}
	}

	@Lazy
	ObjectMapper jsonMapper = {
		def mapper = new ObjectMapper()
		mapper.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
		mapper.registerModule(new JavaTimeModule())
		mapper.setDateFormat(new ISO8601DateFormat())
		mapper
	}()

	/**
	 * Write Json data for coming service answers for debug and test purposes
	 *
	 * @param data
	 * @param filename
	 * @return
	 */
	private writeDebugJson(data, String filename = 'debug.json'){
		if (options.debug){
			jsonMapper.writerWithDefaultPrettyPrinter().writeValue(new File(filename), data)
		}
	}

	/**
	 * Get Tags information for each application provided in option --only-application
	 *
	 * @return Map [AppName:List<RegistryTagInfo>]
	 */
	Map<String, List<RegistryTagInfo>> getTagsDetailsByApplications(){
		GParsPool.withPool(CONCURRENT_APPLICATION_PROCESSING) {
			def ret = client.getCatalog()?.findAll {String app->
				app ==~ options.onlyApplications
			}?.collectParallel {String app->
				[ (app): client.getApplicationTagsDetails(app) ]
			}?.sum() // Unfortunately GPars have no method like CollectEntriesParallel

			writeDebugJson(ret)

			return ret
		} as Map<String, List<RegistryTagInfo>>
	}

	/**
	 * Mark tags for deletion by setting for each appropriate keepByTop/keepByPeriod reason
	 *
	 * @return
	 */
	private Map<String, List<RegistryTagInfo>> prepareListOfTagsToClean(){
		getTagsDetailsByApplications()?.each {app, List<RegistryTagInfo> tags ->
			markTagsForDeletion(tags)
		}
	}

	/**
	 * Decide if concrete tagRegexp should be deleted or kept by found KeepOption by application, tagRegexp and consider period option.
	 *
	 * @param application Application name. Like egaisapp or postgres
	 * @param tagRegexp like _eg-5147-dos
	 * @return same tags as on input, but with set keepBy information
	 */
	private List<RegistryTagInfo> markTagsForDeletion(List<RegistryTagInfo> tags){
		if (!tags) return

		String application = tags[0].application


		KeepOption keepOption = options.getKeepOptionForApplication(application)
//?        if (opt.always && "$application:$tagRegexp" ==~ opt.always) return new KeepDecision(decision: KeepDecision.Decision.KEEP_ALWAYS)
		// 0: - leave all which marked as always kept
//		tags.findAll{tag-> tag.name ==~ options.alwaysKeep}.each {
//			it.keptBy.add(new KeepDecision(decision: KeepDecision.Decision.KEEP_ALWAYS))
//		}

		// 1: group by matching tagRegexp
		def tagsByRegexps = tags.groupBy {RegistryTagInfo tag ->
			keepOption.optionsByTag.reverse().findResult {KeepOption.KeepTagOption tagOpt ->
				( (tag.name =~ tagOpt.tagRegexp) ? tagOpt : null )
			}
		}

		// 2: Process Top and Period limits
		tagsByRegexps.each {KeepTagOption opt, List<RegistryTagInfo> ts->
			if (opt.top) {
				ts.sort(true){ 'time' == options.sort ? it.created : it.name }
				ts.take(opt.top).each {RegistryTagInfo tag->
					tag.keptBy.keepByTop(opt)
				}
			}

			if (opt.period){
				ts.each{RegistryTagInfo tag->
					Long timeDiffSeconds = tag.created.until(now(), ChronoUnit.SECONDS)
					if(timeDiffSeconds > 0 && timeDiffSeconds < opt.period){ // because until also return negative values if build time in the future (f.e. testing case)
						tag.keptBy.keepByPeriod(opt)
					}
				}
			}
		}

		tags
	}

	/**
	 * To exactly known what clock used and allow override it in test
	 */
	private static Clock clock = Clock.systemDefaultZone()

	/**
	 * Introduce separate method for easy test results of keep
	 */
	static ZonedDateTime now(){
//		ZonedDateTime.now(clock)
		ZonedDateTime.parse('2018-03-02T23:24:25+03:00[Europe/Moscow]')
	}
}
