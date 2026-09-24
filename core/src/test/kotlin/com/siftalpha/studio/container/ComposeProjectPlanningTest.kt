package com.siftalpha.studio.container

import com.siftalpha.studio.platform.CapabilityAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposeProjectPlanningTest {
    private val manifest = """
        services:
          web:
            image: nginx:1.27
            ports:
              - "8080:80"
            depends_on:
              db:
                condition: service_started
          db:
            image: postgres:16
            ports:
              - target: 5432
                published: 15432
                protocol: tcp
    """.trimIndent()

    @Test
    fun rootComposeManifestUsesStablePreferenceAndIgnoresNestedFiles() {
        val selected = ComposeProjectDetector.primaryManifest(
            listOf(
                "deploy/compose.yaml",
                "docker-compose.yml",
                "compose.yml",
                "compose.yaml",
            ),
        )
        assertEquals("compose.yaml", selected)
        assertNull(ComposeProjectDetector.primaryManifest(listOf("deploy/compose.yaml")))
    }

    @Test
    fun composePlanExtractsServicesDependenciesAndPorts() {
        val plan = ComposeProjectPlanner.plan(
            relativePaths = listOf("compose.yaml"),
            manifestText = manifest,
            containerAvailability = CapabilityAvailability.AVAILABLE,
        )

        assertEquals(ComposeProjectPlanStatus.READY, plan.status)
        assertEquals(listOf("db", "web"), plan.services.map { it.name })
        val web = plan.services.single { it.name == "web" }
        assertEquals(listOf("db"), web.dependsOn)
        assertEquals(8080, web.ports.single().published)
        assertEquals(80, web.ports.single().target)
        val db = plan.services.single { it.name == "db" }
        assertEquals(15432, db.ports.single().published)
        assertEquals(5432, db.ports.single().target)
    }

    @Test
    fun missingContainerCapabilityIsAPlanFactNotAParseFailure() {
        val unavailable = ComposeProjectPlanner.plan(
            listOf("compose.yaml"),
            manifest,
            CapabilityAvailability.UNAVAILABLE,
        )
        val unknown = ComposeProjectPlanner.plan(
            listOf("compose.yaml"),
            manifest,
            CapabilityAvailability.UNKNOWN,
        )

        assertEquals(ComposeProjectPlanStatus.CAPABILITY_UNAVAILABLE, unavailable.status)
        assertEquals(ComposeProjectPlanStatus.CAPABILITY_UNKNOWN, unknown.status)
        assertTrue(unavailable.issues.isEmpty())
        assertTrue(unknown.issues.isEmpty())
    }

    @Test
    fun invalidServiceDependencyBlocksTheComposePlan() {
        val plan = ComposeProjectPlanner.plan(
            listOf("compose.yml"),
            """
                services:
                  web:
                    image: nginx
                    depends_on:
                      - missing-db
            """.trimIndent(),
            CapabilityAvailability.AVAILABLE,
        )

        assertEquals(ComposeProjectPlanStatus.INVALID_MANIFEST, plan.status)
        assertTrue(plan.issues.single().contains("unknown service"))
    }
}
