package info.hubbitus.cli

import com.beust.jcommander.IStringConverter
import com.beust.jcommander.converters.IParameterSplitter

/**
 * @author Pavel Alexeev.
 * @since 2017-09-18 00:57.
 */
class CleanOptionConverter implements IStringConverter<KeepOption> {
    /**
     * Options will be passed as:
     * --clean='[regex="/egaisapp/", keep-top=10, keep-period=7d, always-keep="/^(tagRegexp|release)_\d\.\d$/"]'
     *
     * @param value
     * @return
     */
    @Override
    KeepOption convert(String value) {
        return KeepOption.fromString(value)
    }

    /**
     * Prevent split by commas value from "--clean='[regex="/egaisapp/", keep-top=10, keep-period=7d, always-keep="/^(tagRegexp|release)_\d\.\d$/"]'"
     */
    static class NopSplitter implements IParameterSplitter {
        @Override
        List<String> split(String value) {
            return [value]
        }
    }
}
