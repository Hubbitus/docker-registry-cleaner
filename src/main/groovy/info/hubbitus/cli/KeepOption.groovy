package info.hubbitus.cli

import com.fasterxml.jackson.annotation.JsonIgnore
import groovy.json.JsonParserType
import groovy.json.JsonSlurper
import groovy.transform.AutoClone
import groovy.transform.ToString
import groovy.util.logging.Slf4j

/**
 * In commandline it will be passed as:
 * --keep=GLOBAL="{ tagRegexp: '^(tagRegexp|release).+$', top: 20, period: 1w }"
 * --keep=egaisapp='[ { tagRegexp: ".+", top: 100, period: 5d }, { tagRegexp: "release_.+", top: 4 }, {tagRegexp: "auto.+", period: "4d"} ]'
 * --keep=glrapp="{ top: 5 }"
 *
 * @author Pavel Alexeev.
 * @since 2017-09-18 00:59.
 */
@Slf4j
@AutoClone
@ToString(excludes = 'optionsByTag', includePackage = false)
class KeepOption {

	@ToString(cache = true, includeNames = true, ignoreNulls = true, includePackage = false) // , excludes = 'parent'
	static class KeepTagOption {
		@JsonIgnore
		KeepOption parent
		/**
		 * Tag of application. Regexp.
		 */
		String tagRegexp = /.*/
		/**
		 * Top N items to keep according to sorting
		 */
		Integer top
		/**
		 * Seconds to expire tagRegexp after build date
		 * For set we allow pass strings with suffixes m, h, d, w for minutes, hours, days and weeks accordingly
		 * @see #setPeriod(java.lang.String)
		 */
		Long period

		/**
		 * Primary for debug
		 */
		String periodStr

		void setPeriodStr(String value){
			setPeriod(value)
		}

		/**
		 * Private constructor.
		 * For create use {@see newTagOption}
		 *
		 * @param parent
		 * @param tagRegexp
		 * @param top
		 * @param period
		 */
		private KeepTagOption(KeepOption parent, String tagRegexp = null, Integer top = null, String period = null) {
			assert parent
			this.parent = parent
			this.tagRegexp = ( tagRegexp ?: this.tagRegexp)
			this.top = top
			setPeriod(period)
		}

		private void setPeriod(String str){
			periodStr = str
			if (str){
				int multiplier = 1
				switch (str[-1]){
					case 'm':
						multiplier = 60
						break

					case 'h':
						multiplier = 60 * 60
						break

					case 'd':
						multiplier = 60 * 60 * 24
						break

					case 'w':
						multiplier = 60 * 60 * 24 * 7
						break
				}
				this.period = (multiplier > 1 ? Integer.parseInt(str[0..-2]) : Integer.parseInt(str)) * multiplier
			}
		}
	}

	/**
	 * Creator with auto-reference to parent
	 *
	 * @param tag
	 * @param top
	 * @param period
	 * @return
	 */
	KeepTagOption newTagOption(String tag = null, Integer top = null, String period = null){
		return new KeepTagOption(this, tag, top, period)
	}

	/**
	 * Name of keep rule
	 */
	String name
	List<KeepTagOption> optionsByTag

	/**
	 * @link fromString should be used instead in most cases
	 */
	KeepOption(String name){
		this.name = name
		optionsByTag = [newTagOption()]
	}

	KeepOption plus(KeepOption other){
		KeepOption ret = this.clone()
		if (null != other){
			ret.optionsByTag += other.optionsByTag
		}
		return ret
	}

	/**
	 * Creator factory
	 *
	 * @param str
	 * @return
	 */
	static KeepOption fromString(String name, String str){
		try {
			def config = new JsonSlurper().setType(JsonParserType.LAX).parseText(str.replaceAll(/(^['"])|(['"]$)/, '')) // Strip quotes required
			KeepOption opt = new KeepOption(name)

			opt.optionsByTag = (config instanceof Map ? [config] : config as List).collect { c->
				opt.newTagOption(c.tag, c.top, c.period)
			}

			opt
		}
		catch (Exception e){
			log.error("Can't parse options from string: <$str>:", e)
			throw e
		}
	}
}
