package com.zpic.launcher

import com.zpic.launcher.data.AppGroup
import com.zpic.launcher.data.GroupInput
import com.zpic.launcher.data.buildGroups
import com.zpic.launcher.data.classify
import com.zpic.launcher.data.moveApp
import com.zpic.launcher.data.placeApps
import org.junit.Assert.assertEquals
import org.junit.Test

class GroupingTest {
    @Test fun appsFromTheBugReportLandSensibly() {
        assertEquals(AppGroup.Finance, classify("in.aura.gold", "Aura", -1))
        assertEquals(AppGroup.Finance, classify("com.snapwork.hdfc", "HDFC Bank", -1))
        assertEquals(AppGroup.Food, classify("com.Dominos", "Domino's", -1))
        assertEquals(AppGroup.Shopping, classify("com.zeptoconsumerapp", "Zepto", -1))
        assertEquals(AppGroup.Learn, classify("com.google.android.apps.classroom", "Classroom", -1))
        assertEquals(AppGroup.Work, classify("com.microsoft.windowsintune.companyportal", "Company Portal", -1))
        assertEquals(AppGroup.Media, classify("com.bittorrent.client", "BitTorrent", -1))
    }

    @Test fun declaredCategoryIsUsedWhenNameSaysNothing() {
        assertEquals(AppGroup.Games, classify("com.foo.bar", "Blorp", 0))
        assertEquals(AppGroup.Other, classify("com.foo.bar", "Blorp", -1))
    }

    @Test fun groupsStayInOneFixedOrder() {
        val items = listOf(GroupInput("c", AppGroup.Work), GroupInput("d", AppGroup.Work), GroupInput("a", AppGroup.Food), GroupInput("b", AppGroup.Food), GroupInput("e", AppGroup.News))
        val out = buildGroups(items)
        assertEquals(listOf(AppGroup.Food, AppGroup.Work, AppGroup.Other), out.map { it.first })
        assertEquals(listOf("c", "d"), out[1].second)
        assertEquals(listOf("e"), out[2].second)
    }

    @Test fun placementKeepsGapsAndSwaps() {
        val s = placeApps(listOf("a", "b", "c"), mapOf("a" to 7))
        assertEquals(7, s["a"]); assertEquals(0, s["b"]); assertEquals(1, s["c"])
        val m = moveApp(s, "b", 7)
        assertEquals(7, m["b"]); assertEquals(0, m["a"])
        assertEquals(30, moveApp(s, "b", 30)["b"])
    }
}

class CapabilityTest {
    @Test fun paymentAppsAreFoundByWhatTheyDo() {
        assertEquals(AppGroup.Finance, classify("com.foo.super", "Super Money", 7))
        assertEquals(AppGroup.Finance, classify("com.foo.bar", "Blorp", 7, AppGroup.Finance))
        assertEquals(AppGroup.Food, classify("com.Dominos", "Domino's", 0, AppGroup.Finance))
    }
}
