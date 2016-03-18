package ucar.unidata.test.util

import spock.lang.Specification

import java.nio.file.Paths

/**
 *
 *
 * @author cwardgar
 * @since 2016-03-18
 */
class OpenedFilesListenerSpec extends Specification {
    def "getSystemPropAsPath success"() {
        setup:
        System.setProperty('foo', 'bar')

        expect:
        OpenedFilesListener.getSystemPropAsPath('foo') == Paths.get('bar')
    }

    def "getSystemPropAsPath falure"() {
        // TODO
    }
}
