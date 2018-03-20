package info.hubbitus.cli

import com.beust.jcommander.DynamicParameter
import com.beust.jcommander.Parameter
import com.beust.jcommander.ParameterException
import com.beust.jcommander.Parameters
import groovy.transform.Memoized

/**
 * @author Pavel Alexeev.
 * @since 2017-09-16 15:47.
 */
@Parameters(separators='=')
class CliOptions {
	@Parameter
	public List<String> parameters = new ArrayList<>()

	@Parameter(names = ['-u', '--registry-url'], description = 'URL to docker registry REST endpoint. Like https://docreg.taskdata.work/v2/', required = true)
	String registryURL
	@Parameter(names = ['-l', '--login'], description = 'Docker registry login', required = true)
	String login
	@Parameter(names = ['-p', '--password'], description = 'Docker registry password', required = false, password = true)
	String password

	@Parameter(names = ['-P', '--password-file'], description = 'Docker registry password, stored in file for security.', required = false)
	String passwordFile

	/**
	 * We get password directly provided fi present, or readcontent of file
	 * @return
	 */
	String getPassword(){
		password ?: new File(passwordFile).text
	}

	@Parameter(names = ['-o', '--only-applications'], description = 'Regexp to match against applications name to process. Tags for other even will not be fetched')
	String onlyApplications

	@Parameter(names = ['-D', '--debug'], description = 'Debug mode - write JSON data for got tags into debug.json file')
   boolean debug = false

//    @Parameter(names = ['-v', '--verbose'], description = 'More verbosity')
//    boolean verbose = false

//        * Exclude items match to `alwaysKeep` (whitelist)
//    @Parameter(names = ['-c', '--clean'], description = """List of rules to clean! F.e.: --clean='[application=/egaisapp/, tagRegexp=/tmp.*/, keepTop=10, keepPeriod='7d', alwaysKeep=/^(tagRegexp|release)_\\\\d\\\\.\\\\d\$/]'
//    It has next logic:
//        * Firstly match "application" against `application` regexp
//        * Then "tagRegexp" against `tagRegexp` regexp
//        * If `keepPeriod` present and greater than 0 check it matches build time early then in `keepPeriod` (which is by default number of seconds, but suffixes m, h, d, w also supported for minutes, hours, days and weeks)
//        * If `keepTop` present and greater than 0, from above results exempt from deletion that amount of elements, according to sorting, provided in --sort
//          If both `keepPeriod` and `keepTop` provided only tags *match both criteria* will be kept! F.e.:
//            - Said by date matched 10 tags and you have top=5 - so only 5 will be kept - other deleted
//            - In configured date period was 10 tags and you set top=20 - only 10 will be kept - other deleted
//        All other will be deleted!
//        If tagRegexp match to the more than one --clean rule, first found used
//    Please be careful with spaces and quotes. Expression parsed by ConfigSlurper"""
//        , converter = CleanOptionConverter.class, splitter = CleanOptionConverter.NopSplitter.class
//    )
//    List<KeepOption> cleans

	/**
	 * For now converters does not implemented for @DynamicParameter
	 * @issue https://github.com/cbeust/jcommander/issues/400
	 * @see #getKeeps()
	 */
	@DynamicParameter(names = '--keep', description = 'Dynamic parameters go here', required = true)
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

//	--always-keep='^(dir/application:some-tag1|application1:some-tag2)$'
//	@Parameter(names = '--always-keep', description = 'Regexp of match against "application:tagRegexp" which must be kept always! Some sort of important guard to do not hard deal with exclusions in --keep* options')
//	String alwaysKeep

	@Parameter(names = '--sort', description = 'Sort method on optionsByTag list. Either "name" or "time" (build time of image, default)', validateWith = SortOptionValidator.class)
	String sort = 'time'

	@Parameter(names = ['-f', '--format'], description = 'Format of printing tagRegexp line. See SimpleTemplateEngine description and info.hubbitus.RegistryTagInfo class for available data', validateWith = SimpleTemplateEngineFormatValidator.class)
	String format = 'tag=[<% printf("%-" + (tag.application.length() + 1 + tags.max{ it.name.length()}.name.length()) + "s", "${tag.application}:${tag.name}")%>]; time=[${tag.created}]; isForDelete=[<%printf("%-5s", tag.keptBy.isForDelete())%>]; [${tag.keptBy}]'

	@Parameter(names = ['-d', '--delete'], description = '''Delete old optionsByTag!
	By default we do not delete anything. Just list applications and optionsByTag. Information about tagRegexp and its build time also provided. If there any --clean options present also mark optionsByTag which supposed to be deleted.
	For sort you may also use --sort option.''')
	boolean delete = false

	@Parameter(names = '--help', help = true)
	boolean help

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
	 * After parse all values we also want postValidate some complex conditions like dependent values. F.e. if one of tywo options must be filled. So we can't there just mark any of them as required.
	 */
	void postValidate(){
//		if (!password && !passwordFile){
//			throw new ParameterException('You must provide neither `--password` or `--password-file` option to be able ')
//		}
	}
}
