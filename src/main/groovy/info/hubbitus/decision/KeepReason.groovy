package info.hubbitus.decision

import groovy.transform.TupleConstructor

/**
 * @author Pavel Alexeev.
 * @since 2018-02-28 00:43.
 */
@TupleConstructor
enum KeepReason {
	KEEP_BY_TOP,
	KEEP_BY_PERIOD
}