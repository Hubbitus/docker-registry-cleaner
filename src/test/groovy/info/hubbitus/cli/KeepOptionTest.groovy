package info.hubbitus.cli

import spock.lang.Specification
import spock.lang.Unroll

/**
 * @author Pavel Alexeev.
 * @since 2017-09-24 22:01.
 */
class KeepOptionTest extends Specification {
    def 'test plus'() {
        given:
            KeepOption opt1 = KeepOption.fromString(null, '''{ tagRegexp: '^(tagRegexp|release).+$', top: 20, period: 1w }''')
            KeepOption opt2 = KeepOption.fromString(null, '{ top: 5 }')
        when:
            KeepOption sum = opt1 + opt2
        then:
            noExceptionThrown()
            sum
            sum.optionsByTag.size() == 2
            sum.optionsByTag[0].tagRegexp == '^(tagRegexp|release).+$'
            sum.optionsByTag[0].top == 20
            sum.optionsByTag[0].period == 604800
            sum.optionsByTag[1].tagRegexp == '.*'
            sum.optionsByTag[1].top == 5
            sum.optionsByTag[1].period == null

        when:
            sum = opt1 + null
        then:
            noExceptionThrown()
            sum
            sum.optionsByTag.size() == 1
            sum.optionsByTag[0].tagRegexp == '^(tagRegexp|release).+$'
            sum.optionsByTag[0].top == 20
            sum.optionsByTag[0].period == 604800
    }

    @Unroll
    def 'test fromString [#str]'() {
        when:
            KeepOption opt = KeepOption.fromString(null, str)
        then:
            noExceptionThrown()
            opt
            opt.optionsByTag.eachWithIndex { KeepOption.KeepTagOption tag, i ->
                tag.properties.findAll{ !(it.key in ['class', 'parent']) }.each{ prop->
                    assert tag."${prop.key}" == map[i]."${prop.key}"
                }
            }
        where:
            str | map
            '''{ tagRegexp: '^(tagRegexp|release).+$', top: 20, period: 1w }''' | [ [tag: /^(tagRegexp|release).+$/, top: 20, period: 604800] ]
            '{ top: 5, always: "^release$" }' | [ [tag: /.*/, top: 5, always: /^release$/ ] ]
            '[ { tagRegexp: ".+", top: 100, period: 5d }, { tagRegexp: "release_.+", top: 4 }, {tagRegexp: "auto.+", period: "4d"} ]' | [ [tag:'.+', top: 100, period: 432000], [tag:'release_.+', top: 4, period: null], [tag:'auto.+', period: 345600, top: null] ]
    }
}
