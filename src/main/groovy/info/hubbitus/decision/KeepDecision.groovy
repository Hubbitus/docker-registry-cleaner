package info.hubbitus.decision

import groovy.transform.ToString
import info.hubbitus.cli.KeepOption.KeepTagOption

/**
 * @author Pavel Alexeev.
 * @since 2018-02-27 23:42.
 */
@ToString(includeNames = true, includePackage = false, cache = true)
class KeepDecision {
	/**
	 * By what rule option decision made (tagRegexp match) if any
	 */
	KeepTagOption keepTagOption
//	Set<KeepReason> decisions = []
//
//	void add(KeepReason decision, KeepTagOption keepTagOption){
//		assert (null == this.keepTagOption || this.keepTagOption == keepTagOption) && null != keepTagOption
//		decisions += decision
//		this.keepTagOption = keepTagOption
//	}

	boolean keepByTop = false
	boolean keepByPeriod = false

	void keep(KeepTagOption keepTagOption, Boolean keepByTop = null, Boolean keepByPeriod = null){
		assert (null == this.keepTagOption || this.keepTagOption == keepTagOption) && null != keepTagOption

		this.keepTagOption = keepTagOption
		if (null != keepByTop){
			this.keepByTop = keepByTop
		}
		if(null != keepByPeriod){
			this.keepByPeriod == keepByPeriod
		}
	}

	void keepByTop(KeepTagOption keepTagOption, boolean keepByTop = true){
		keep(keepTagOption, keepByTop)
	}

	void keepByPeriod(KeepTagOption keepTagOption, boolean keepByPeriod = true){
		keep(keepTagOption, null, keepByPeriod)
	}

	/**
	 * Summary by all decisions about tags:
	 * - if at least one have
	 *
	 * @return
	 */
	boolean isForDelete(){
		if (null == keepTagOption) return true

		assert keepTagOption.top || keepTagOption.period

		!(
			( keepTagOption.top && keepTagOption.period && keepByTop && keepByPeriod ) ||
				( keepTagOption.top && keepByTop) ||
				( keepTagOption.period && keepByPeriod )
		)
	}
}
