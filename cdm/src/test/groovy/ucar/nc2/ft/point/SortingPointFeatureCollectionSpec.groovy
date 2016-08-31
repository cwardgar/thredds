package ucar.nc2.ft.point

import org.junit.Assert
import spock.lang.Shared
import spock.lang.Specification
import ucar.nc2.constants.FeatureType
import ucar.nc2.ft.*
import ucar.nc2.time.CalendarDateUnit
import ucar.unidata.util.point.PointTestUtil

/**
 * Tests SortingPointFeatureCollection.
 *
 * @author cwardgar
 * @since 2015-11-02
 */
class SortingPointFeatureCollectionSpec extends Specification {
    @Shared FeatureDatasetPoint contigRaggedFdPoint
    @Shared FlattenedPointCollection contigRaggedFlatPfc
    
    def setupSpec() {
        // We're going to be using these point features a lot, so only read them in once.
        contigRaggedFdPoint = PointTestUtil.openClassResourceAsPointDataset(getClass(), "continuousRagged.ncml")
        contigRaggedFlatPfc = new FlattenedPointCollection(contigRaggedFdPoint.pointFeatureCollectionList)
    }
    
    def cleanupSpec() {
        contigRaggedFlatPfc?.finish()
        contigRaggedFdPoint?.close()
    }
    
    
    def "metadata of empty collection"() {
        setup: "empty collection"
        PointFeatureCollection sortingPfc = new SortingPointFeatureCollection()
        
        expect: "metadata has default values"
        sortingPfc.altUnits == null
        sortingPfc.boundingBox == null
        sortingPfc.calendarDateRange == null
        sortingPfc.collectionFeatureType == FeatureType.ANY_POINT
        sortingPfc.extraVariables?.isEmpty()
        sortingPfc.name == sortingPfc.class.simpleName  // Always the same.
        sortingPfc.nobs == 0
        sortingPfc.timeUnit == CalendarDateUnit.unixDateUnit
        sortingPfc.size() == 0
        
        and: "iterators are empty"
        !sortingPfc.hasNext()
        !sortingPfc.pointFeatureIterator.hasNext()
        
        cleanup: "used internal iterator, so much finish()"
        sortingPfc.finish()
    }
    
    def "metadata of non-empty collection"() {
        setup: "sortingPfc sorts contigRaggedFlatPfc using the default comparator"
        PointFeatureCollection sortingPfc = new SortingPointFeatureCollection()
        sortingPfc.addAll contigRaggedFlatPfc
        
        expect: "metadata will mirror metadata of contigRaggedFlatPfc"
        // timeUnit, altUnits, and featType will be copied directly from contigRaggedFlatPfc. The others are computed.
        sortingPfc.altUnits              == contigRaggedFlatPfc.altUnits
        sortingPfc.boundingBox           == contigRaggedFlatPfc.boundingBox
        sortingPfc.calendarDateRange     == contigRaggedFlatPfc.calendarDateRange
        sortingPfc.collectionFeatureType == contigRaggedFlatPfc.collectionFeatureType
        sortingPfc.extraVariables        == contigRaggedFlatPfc.extraVariables
        sortingPfc.name                  == sortingPfc.class.simpleName  // Always the same.
        sortingPfc.nobs                  == contigRaggedFlatPfc.nobs
        sortingPfc.timeUnit              == contigRaggedFlatPfc.timeUnit
        sortingPfc.size()                == contigRaggedFlatPfc.size()
    }
    
    def "can't add() after getPointFeatureIterator()"() {
        setup: "sortingPfc sorts contigRaggedFlatPfc using the default comparator"
        PointFeatureCollection sortingPfc = new SortingPointFeatureCollection()
        sortingPfc.addAll contigRaggedFlatPfc
        
        and: "get iterator"
        sortingPfc.pointFeatureIterator
        
        when: "try to add more features"
        sortingPfc.addAll contigRaggedFlatPfc
    
        then: "IllegalStateException is thrown"
        IllegalStateException e = thrown()  // From SortingPointFeatureCollection.add()
        e.message == "No more features can be added once getPointFeatureIterator() is called."
    }
    
    def "timeUnit must match"() {
        setup: "sortingPfc sorts contigRaggedFlatPfc using the default comparator"
        PointFeatureCollection sortingPfc = new SortingPointFeatureCollection()
        sortingPfc.addAll contigRaggedFlatPfc
        
        and: "define mock feature that has a different timeUnit than sortingPfc"
        def pointFeat = Mock(PointFeature) {
            getFeatureCollection() >> Mock(DsgFeatureCollection) {
                getTimeUnit() >> CalendarDateUnit.of(null, "minutes since 1984-06-25 19:47:00")
                // altUnits and featType don't matter here because timeUnit is checked first.
            }
        }
        
        when: "try to add the feature"
        sortingPfc.add pointFeat
    
        then: "IllegalArgumentException is thrown"
        IllegalArgumentException e = thrown()  // From SortingPointFeatureCollection.add()
        e.message.endsWith "All features must have 'Day since 1970-01-01T00:00:00Z'."
    }
    
    def "altUnits must match"() {
        setup: "sortingPfc sorts contigRaggedFlatPfc using the default comparator"
        PointFeatureCollection sortingPfc = new SortingPointFeatureCollection()
        sortingPfc.addAll contigRaggedFlatPfc
    
        and: "define mock feature that has a different altUnits than sortingPfc"
        def pointFeat = Mock(PointFeature) {
            getFeatureCollection() >> Mock(DsgFeatureCollection) {
                getTimeUnit() >> CalendarDateUnit.of(null, "day since 1970-01-01 00:00:00")  // Same as sortingPfc
                getAltUnits() >> "yards"
                // featType don't matter here because timeUnit and altUnits are checked first.
            }
        }
    
        when: "try to add the feature"
        sortingPfc.add pointFeat
    
        then: "IllegalArgumentException is thrown"
        IllegalArgumentException e = thrown()  // From SortingPointFeatureCollection.add()
        e.message.endsWith "All features must have 'mm'."
    }
    
    def "featType must match"() {
        setup: "sortingPfc sorts contigRaggedFlatPfc using the default comparator"
        PointFeatureCollection sortingPfc = new SortingPointFeatureCollection()
        sortingPfc.addAll contigRaggedFlatPfc
    
        and: "define mock feature that has a different altUnits than sortingPfc"
        def pointFeat = Mock(PointFeature) {
            getFeatureCollection() >> Mock(DsgFeatureCollection) {
                getTimeUnit() >> CalendarDateUnit.of(null, "day since 1970-01-01 00:00:00")  // Same as sortingPfc
                getAltUnits() >> "mm"                                                        // Same as sortingPfc
                getCollectionFeatureType() >> FeatureType.TRAJECTORY
            }
        }
    
        when: "try to add the feature"
        sortingPfc.add pointFeat
    
        then: "IllegalArgumentException is thrown"
        IllegalArgumentException e = thrown()  // From SortingPointFeatureCollection.add()
        e.message.endsWith "All features must have 'STATION'."
    }
    
    def "iterators"() {
        setup: "sortingPfc sorts contigRaggedFlatPfc using the default comparator"
        PointFeatureCollection sortingPfc = new SortingPointFeatureCollection()
        sortingPfc.addAll contigRaggedFlatPfc
        
        expect: "hasNext() is called several times without accompanying next(), always returning true"
        PointFeatureIterator iter = sortingPfc.pointFeatureIterator
        10.times { iter.hasNext() }
        
        and: "There are 3 features, each with a different time value"
        iter.next().featureData.getScalarDouble("time") == 120
        iter.next().featureData.getScalarDouble("time") == 150
        iter.next().featureData.getScalarDouble("time") == 180
        
        and: "hasNext() is false"
        !iter.hasNext()
        
        when: "next() is called on an iterator with no more features"
        iter.next()
        
        then: "NoSuchUnitException is thrown"
        NoSuchElementException e2 = thrown()  // From PointIteratorAdapter.next()
        e2.message == "The iteration has no more elements."
        
        // Internal iterator
        when: "Call next() before hasNext()"
        sortingPfc.next()
    
        then: "exception is thrown"
        IllegalStateException e1 = thrown()
        e1.message == "Call hasNext() first!"
        
        cleanup:
        iter.close()
    }
    
    def "resetIteration() finish()es previous internal iterator"() {
        setup: "Create a spy so we can examine method invocations. It sorts using default comparator"
        PointFeatureCollection sortingPfcSpy = Spy(SortingPointFeatureCollection)
        sortingPfcSpy.addAll contigRaggedFlatPfc

        when: "call resetIteration() in the the middle of an iteration"
        sortingPfcSpy.hasNext()
        sortingPfcSpy.next()
        sortingPfcSpy.resetIteration()
        
        then: "finish() is called once"
        1 * sortingPfcSpy.finish()
    }
    
    def "internal iterator is finish()ed when there are no more elements"() {
        setup: "Create a spy so we can examine method invocations. It sorts using default comparator"
        PointFeatureCollection sortingPfcSpy = Spy(SortingPointFeatureCollection)
        sortingPfcSpy.addAll contigRaggedFlatPfc

        when: "iterate through all elements in a 3-element PFC"
        3.times {
            assert sortingPfcSpy.hasNext()
            sortingPfcSpy.next()
        }

        and: "do a final hasNext()"
        assert !sortingPfcSpy.hasNext()

        then: "finish() is called once"
        1 * sortingPfcSpy.finish()
    }

    def "different sort methods, simple comparator"() {
        setup: "Open test file and flatten the PFCs within"
        FeatureDatasetPoint fdPoint = PointTestUtil.openClassResourceAsPointDataset(getClass(), "orthogonal.ncml")
        FlattenedPointCollection flattenedPfc = new FlattenedPointCollection(fdPoint.pointFeatureCollectionList)

        and: "Create comparator that will sort elements in reverse order of station name."
        def revStationNameComp = SortingPointFeatureCollection.stationNameComparator.reversed()

        and: "sortingPfc sorts flattenedPfc using revStationNameComp"
        PointFeatureCollection sortingPfc = new SortingPointFeatureCollection(revStationNameComp)
        sortingPfc.addAll flattenedPfc

        expect: "Sorted list and SortingPointFeatureCollection have same iteration order when using same comparator."
        PointTestUtil.assertIterablesEquals flattenedPfc.asList().toSorted(revStationNameComp), sortingPfc.asList()

        cleanup: "Close dataset"
        fdPoint?.close()
    }
    
    def "value comparison, complex comparator"() {
        setup: "Open the input file and flatten the PFCs within"
        FeatureDatasetPoint fdPointInput =
                PointTestUtil.openClassResourceAsPointDataset(getClass(), "cacheTestInput1.ncml")
        FlattenedPointCollection inputPfc =
                new FlattenedPointCollection(fdPointInput.pointFeatureCollectionList)
    
        and: "Create first comparator. It orders by observation time"
        def obsTimeComp = new OrderBy({ it.observationTime })
    
        and: "Reverse order of the length of the station's name, e.g. '&&' < '&&&&, but '11' == '22'"
        def revStationNameLenComp = new OrderBy({ (it as StationPointFeature).station.name.length() }).reversed()
    
        and: "Combine the comparators. First use obsTimeComp, then break ties with revStationNameLenComp"
        def compositeComp = obsTimeComp.thenComparing revStationNameLenComp
        
        and: "Dump input into SortingPointFeatureCollection"
        PointFeatureCollection sortingPfc = new SortingPointFeatureCollection(compositeComp)
        sortingPfc.addAll inputPfc
        
        
        expect: "same station names"
        sortingPfc.collect({ (it as StationPointFeature).station.name }).unique() == [
            '7777777', '666666', '55555', '4444', '333', '22', '1' ]
        
        and: "same observation times"
        // Builds the list [ 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3 ]
        // I wanted to see how obnoxious I could be with Groovy though. Pretty damn obnoxious, it turns out.
        def expectedTimes = (1..3).collectMany { time -> (1..7).collect { time } }
        sortingPfc.collect { it.observationTime } == expectedTimes
        
        and: "same humidities"
        def expectedHumidities = (1..3).collectMany { time -> (7..1).collect { it + time * 0.1 } }
        def actualHumidities = sortingPfc.collect { it.featureData.getScalarFloat("humidity") }
        Assert.assertArrayEquals(expectedHumidities as float[], actualHumidities as float[], 0.01)


        cleanup: "Close dataset"
        fdPointInput?.close()
    }
}
