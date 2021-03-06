/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jfrog.wharf.resolver

import org.jfrog.wharf.ivy.resolver.IBiblioWharfResolver
import spock.lang.Specification

/**
 * @author Hans Dockter
 */
class WharfResolverTest extends Specification {
    WharfResolverConfiguration wharfResolver = new WharfResolverConfiguration()

    def testInit() {
        expect:
        wharfResolver.getSnapshotTimeout().is(IBiblioWharfResolver.DAILY)
    }

    def timeoutStrategyNever_shouldReturnAlwaysFalse() {
        expect:
        !IBiblioWharfResolver.NEVER.isCacheTimedOut(0)
        !IBiblioWharfResolver.NEVER.isCacheTimedOut(System.currentTimeMillis())
    }

    def timeoutStrategyAlways_shouldReturnAlwaysTrue() {
        expect:
        IBiblioWharfResolver.ALWAYS.isCacheTimedOut(0)
        IBiblioWharfResolver.ALWAYS.isCacheTimedOut(System.currentTimeMillis())
    }

    def timeoutStrategyDaily() {
        expect:
        !IBiblioWharfResolver.DAILY.isCacheTimedOut(System.currentTimeMillis())
        IBiblioWharfResolver.ALWAYS.isCacheTimedOut(System.currentTimeMillis() - 24 * 60 * 60 * 1000)
    }

    def timeoutInterval() {
        def interval = new WharfResolverConfiguration.Interval(1000)

        expect:
        interval.isCacheTimedOut(System.currentTimeMillis() - 5000)
        !interval.isCacheTimedOut(System.currentTimeMillis())
    }

    def setTimeoutByMilliseconds() {
        when:
        wharfResolver.setSnapshotTimeout(1000)

        then:
        ((IBiblioWharfResolver.Interval) wharfResolver.getSnapshotTimeout()).interval == 1000
    }

    def setTimeoutByStrategy() {
        when:
        wharfResolver.setSnapshotTimeout(IBiblioWharfResolver.NEVER)

        then:
        wharfResolver.getSnapshotTimeout().is(IBiblioWharfResolver.NEVER)
    }
}
