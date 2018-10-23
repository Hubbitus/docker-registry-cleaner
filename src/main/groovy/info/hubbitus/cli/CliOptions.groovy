package info.hubbitus.cli

import com.beust.jcommander.DynamicParameter
import com.beust.jcommander.Parameter
import com.beust.jcommander.ParameterException
import com.beust.jcommander.Parameters
import groovy.transform.Memoized
import groovy.util.logging.Slf4j

/**
 * @author Pavel Alexeev.
 * @since 2017-09-16 15:47.
 */
@Slf4j
@Parameters(separators='=')
class CliOptions {
	@Parameter
	public List<String> parameters = new ArrayList<>()

	@Parameter(names = ['-u', '--registry-url'], description = 'URL to docker registry REST endpoint. Like https://docreg.taskdata.work/v2/', required = true)
	String registryURL
	@Parameter(names = ['-l', '--login'], description = 'Docker registry login', required = true)
	String login
	@Parameter(names = ['-p', '--password'], description = 'Docker registry password', required = false)
	String password

	@Parameter(names = ['-P', '--password-file'], description = 'Docker registry password, stored in file for security.', required = false)
	String passwordFile

	/**
	 * We get {@see password} directly if present, or read content of file from {@see passwordFile}
	 * @return
	 */
	String getPassword(){
		if (!(password || passwordFile))
			throw new ParameterException("Authentication required to operate! So, please provide --password of --passwordFile option to continue")
		password ?: new File(passwordFile).text
	}

	@Parameter(names = ['-o', '--only-applications'], description = 'Regexp to match against applications name to process. Tags for other even will not be fetched')
	String onlyApplications

	@Parameter(names = ['-D', '--debug'], description = 'Debug mode - write JSON data for got tags into debug.json file')
	boolean debug = false

	@Parameter(names = ['-i', '--interactive'], description = 'Interactive mode. Ask for deletion each image. Details like data and why it is proposed for delete will be shown. Implies delete = true')
	boolean interactive = false

	/**
	 * For now converters does not implemented for {@see com.beust.jcommander.DynamicParameter}:
	 * @issue https://github.com/cbeust/jcommander/issues/400
	 * @see #getKeeps()
	 */
	@DynamicParameter(names = '--keep', required = true, description = '''As primary goal to delete some old images we just provide some opposite rules which must be kept.
				1) We process only applications, matches --only-applications pattern if it set.
				2) Firstly match "application" against `application` regexp
				3) Then `tag` name against `tag` regexp
				4) If `period` present and greater than 0 check it matches build time early then in `period` (which is by default number of seconds, but suffixes m, h, d, w also supported for minutes, hours, days and weeks)
				5) If `top` present and greater than 0, from above results exempt from deletion that amount of elements, according to sorting, provided in --sort
				6) If both `period` and `top` provided only tags *match both criteria* will be kept (boolean AND)!
				F.e.:
					- Said by date matched 10 tags and you have top=5 - so only 5 will be kept - other deleted
					- In configured date period was 10 tags and you set top=20 - only 10 will be kept - other deleted

				All other will be deleted!

				Example of options (to provide in file, please quote properly in shell options):
				--keep=GLOBAL={ top: 5, period: 1w }
				--keep=egaisapp=[ { tag: ".+", top: 10, period: 3d }, { tag: "release_.+", top: 4 }, {tag: "auto.+", period: "4d"} ]
				--keep=bp-app=[ { tag: "^(dev|master)$", top: 2 }, { tag: "^dev-", top: 5 }, {tag: "^master-", period: "4d"} ]
				--keep=glrapp={ top: 4 }
''')
	private Map<String,String> _keeps = [:];

	/**
	 * For now converters does not implemented for @DynamicParameter
	 * @issue https://github.com/cbeust/jcommander/issues/400
	 * so use as workaround manual converting
	 *
	 * @return
	 */
	@Memoized
	Map<String,KeepOption> getKeeps(){
		(Map<String,KeepOption>)_keeps.collectEntries {
			[ (it.key): KeepOption.fromString(it.key, it.value) ]
		}
	}

	@Parameter(names = '--sort', description = 'Sort method on optionsByTag list. Either "name" or "time" (build time of image, default). In case of time sorting most recent should be stay, so DESC assuming', validateWith = SortOptionValidator.class)
	String sort = 'time'

	@Parameter(names = ['-f', '--format'], description = 'Format of printing tagRegexp line. See SimpleTemplateEngine description and info.hubbitus.RegistryTagInfo class for available data', validateWith = SimpleTemplateEngineFormatValidator.class)
	String format = 'tag=[<% printf("%-" + (tag.application.length() + 1 + tags.max{ it.name.length()}.name.length()) + "s", "${tag.application}:${tag.name}")%>]; time=[${tag.created}]; isForDelete=[<%printf("%-5s", tag.keptBy.isForDelete())%>]; [${tag.keptBy}]'

	@Parameter(names = ['-d', '--delete'], description = '''Really perform deletion!
				By default we do not delete anything. Just list found tags per application. Information about tagRegexp and its build time also provided. If there any --keep options present also mark tags which supposed for deletion.
				For sort you may also use --sort option.''')
	boolean delete = false

	@Parameter(names = '--help', help = true)
	boolean help

	@Parameter(names = ['-a', '--after-command'], description = '''Command, which should be executed at end of work. Most probably you want run something like:
				bash -c '/usr/bin/docker exec docker-registry registry garbage-collect /etc/docker/registry/config.yml && /usr/bin/docker restart docker-registry'
				Warning! If you run cleaner from docker, don't forget mount /run/docker.sock or provide TCP access to the daemon!''', required = false)
	String afterCommand

	/**
	 * Merges GLOBAL application options with particular one, if any present. And return 'Effective' one.
	 *
	 * @param application
	 * @return
	 */
	KeepOption getKeepOptionForApplication(String application){
		if (keeps[application]){
			return keeps.GLOBAL + keeps[application]
		}
		else{
			return keeps.GLOBAL
		}
	}

	/**
	 * After parse all values we also want postValidate some complex conditions like dependent values. F.e. if one of two options must be filled. So we can't there just mark any of them as required.
	 */
	void postValidate(){
		if (interactive && !delete){
			log.warn("interactive option provided, delete=true also implied!")
			delete = true
		}
	}
}
