package edu.ucar.build

import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 *
 *
 * @author cwardgar
 * @since 2016-03-10
 */
class LoggingTestListener implements TestListener {
    private static final Logger logger = LoggerFactory.getLogger('testData')

    @Override
    void beforeSuite(TestDescriptor suite) {

    }

    @Override
    void afterSuite(TestDescriptor suite, TestResult result) {

    }

    @Override
    void beforeTest(TestDescriptor descriptor) {
        logger.warn "BEGIN ${descriptor.className}.${descriptor.name}()"
    }

    @Override
    void afterTest(TestDescriptor descriptor, TestResult result) {
        logger.warn "END   ${descriptor.className}.${descriptor.name}()"
    }
}
