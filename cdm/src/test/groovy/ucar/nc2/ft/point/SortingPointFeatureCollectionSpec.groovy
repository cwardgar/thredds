package ucar.nc2.ft.point

import org.junit.Assert
import spock.lang.Specification
import ucar.nc2.constants.FeatureType
import ucar.nc2.ft.FeatureDatasetPoint
import ucar.nc2.ft.PointFeatureCollection
import ucar.nc2.ft.PointFeatureIterator
import ucar.nc2.time.CalendarDateUnit
import ucar.unidata.util.point.PointTestUtil

/**
 * @author cwardgar
 * @since 2015-11-02
 */
class SortingPointFeatureCollectionSpec extends Specification {
    def "empty collection"() {
        setup: "empty collection"
        PointFeatureCollection sortingPfc = new SortingPointFeatureCollection()
        
        expect: "metadata has default values"
        sortingPfc.altUnits == null
        sortingPfc.boundingBox == null
        sortingPfc.calendarDateRange == null
        sortingPfc.collectionFeatureType == FeatureType.ANY_POINT
        sortingPfc.extraVariables?.isEmpty()
        sortingPfc.name == "tempName"
        sortingPfc.nobs == 0
        sortingPfc.timeUnit == CalendarDateUnit.unixDateUnit
        sortingPfc.size() == 0
        
        and: "iterators are empty"
        !sortingPfc.hasNext()
        !sortingPfc.pointFeatureIterator.hasNext()
    }
    
    def "iterators"() {
        setup: "Open test file and flatten the PFCs within"
        FeatureDatasetPoint fdPoint = PointTestUtil.openClassResourceAsPointDataset(getClass(), "continuousRagged.ncml")
        FlattenedPointCollection flattenedPfc = new FlattenedPointCollection(fdPoint.pointFeatureCollectionList)
    
        and: "sortingPfc sorts flattenedPfc using the default comparator"
        PointFeatureCollection sortingPfc = new SortingPointFeatureCollection()
        sortingPfc.addAll flattenedPfc
        
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
        setup:
        def sortingPfc = createSortingPfcSpy()

        when: "call resetIteration() in the the middle of an iteration"
        sortingPfc.hasNext()
        sortingPfc.next()
        sortingPfc.resetIteration()
        
        then: "finish() is called once"
        1 * sortingPfc.finish()
    }
    
    def "internal iterator is finish()ed when there are no more elements"() {
        setup:
        def sortingPfc = createSortingPfcSpy()

        when: "iterate through all elements in a 3-element PFC"
        3.times {
            assert sortingPfc.hasNext()
            sortingPfc.next()
        }

        and: "do a final hasNext()"
        assert !sortingPfc.hasNext()

        then: "finish() is called once"
        1 * sortingPfc.finish()
    }
    
    SortingPointFeatureCollection createSortingPfcSpy() {
        // Open test file and flatten the PFCs within
        FeatureDatasetPoint fdPoint = PointTestUtil.openClassResourceAsPointDataset(getClass(), "continuousRagged.ncml")
        FlattenedPointCollection flattenedPfc = new FlattenedPointCollection(fdPoint.pointFeatureCollectionList)
        
        // Create a Spy for SortingPointFeatureCollection and sort flattenedPfc using the default comparator
        def sortingPfc1 = Spy(SortingPointFeatureCollection)
        sortingPfc1.addAll flattenedPfc
        
        return sortingPfc1
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

        cleanup:
        flattenedPfc?.finish()
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


        cleanup: "Close or finish resources, in reverse order that they were acquired"
        sortingPfc?.finish()
        inputPfc?.finish()
        fdPointInput?.close()
    }
}
