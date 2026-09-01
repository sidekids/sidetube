package io.github.degipe.youtubewhitelist.core.export.content

import com.google.common.truth.Truth.assertThat
import io.github.degipe.youtubewhitelist.core.common.model.WhitelistItemType
import io.github.degipe.youtubewhitelist.core.common.result.AppResult
import io.github.degipe.youtubewhitelist.core.database.dao.KidProfileDao
import io.github.degipe.youtubewhitelist.core.database.dao.WhitelistItemDao
import io.github.degipe.youtubewhitelist.core.database.entity.KidProfileEntity
import io.github.degipe.youtubewhitelist.core.database.entity.WhitelistItemEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class StarterPackServiceImplTest {

    private lateinit var whitelistItemDao: WhitelistItemDao
    private lateinit var kidProfileDao: KidProfileDao
    private lateinit var service: StarterPackServiceImpl

    private val profileId = "profile-1"

    private val profile = KidProfileEntity(
        id = profileId, parentAccountId = "parent-1", name = "Mira",
        bedtimeEnabled = false, bedtimeStartMinutes = 21 * 60, bedtimeEndMinutes = 7 * 60,
        bedtimeWeekendOffsetMinutes = 0
    )

    private val library = """
        {
          "version": 1,
          "id": "alter-9-11",
          "title": "Startpaket 9-11 Jahre",
          "note": "Kuratiert",
          "profilePreset": { "ageBand": "kids", "bedtimeEnabled": true, "bedtimeStartMinutes": 1200,
                             "bedtimeEndMinutes": 390, "bedtimeWeekendOffsetMinutes": 60 },
          "videos": [
            { "id": "aaa", "title": "Erstes Video", "channelId": "UC1", "channelTitle": "Die Maus",
              "category": "knowledge", "ageMin": 7, "note": "FLIMMO" },
            { "id": "bbb", "title": "Zweites Video", "channelTitle": "Checker Tobi" }
          ]
        }
    """.trimIndent()

    private val source = object : StarterPackSource {
        var files = mapOf("alter-9-11.json" to library)
        override fun listLibraries() = files.keys.sorted()
        override fun read(fileName: String) = files[fileName]
    }

    @Before
    fun setUp() {
        whitelistItemDao = mockk(relaxed = true)
        kidProfileDao = mockk(relaxed = true)
        service = StarterPackServiceImpl(source, whitelistItemDao, kidProfileDao)
    }

    @Test
    fun `availablePacks liest Titel und Anzahl aus der Datei`() = runTest {
        val packs = service.availablePacks()

        assertThat(packs).hasSize(1)
        assertThat(packs[0].id).isEqualTo("alter-9-11")
        assertThat(packs[0].title).isEqualTo("Startpaket 9-11 Jahre")
        assertThat(packs[0].videoCount).isEqualTo(2)
        assertThat(packs[0].hasProfilePreset).isTrue()
    }

    @Test
    fun `import legt Videos im gewaehlten Profil an`() = runTest {
        coEvery { whitelistItemDao.findByYoutubeId(profileId, any()) } returns null
        coEvery { kidProfileDao.getProfileByIdOnce(profileId) } returns profile
        val items = mutableListOf<WhitelistItemEntity>()
        coEvery { whitelistItemDao.insert(capture(items)) } returns Unit

        val result = service.import("alter-9-11.json", profileId, applyPreset = false)

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        val success = (result as AppResult.Success).data
        assertThat(success.added).isEqualTo(2)
        assertThat(success.skipped).isEqualTo(0)
        assertThat(items.map { it.youtubeId }).containsExactly("aaa", "bbb")
        assertThat(items.all { it.type == WhitelistItemType.VIDEO }).isTrue()
        assertThat(items.all { it.kidProfileId == profileId }).isTrue()
        assertThat(items[0].thumbnailUrl).isEqualTo("https://i.ytimg.com/vi/aaa/hqdefault.jpg")
        assertThat(items[0].channelTitle).isEqualTo("Die Maus")
    }

    @Test
    fun `import ueberspringt vorhandene Videos statt Dubletten anzulegen`() = runTest {
        coEvery { whitelistItemDao.findByYoutubeId(profileId, "aaa") } returns
            WhitelistItemEntity(
                id = "vorhanden", kidProfileId = profileId, type = WhitelistItemType.VIDEO,
                youtubeId = "aaa", title = "Erstes Video", thumbnailUrl = "egal"
            )
        coEvery { whitelistItemDao.findByYoutubeId(profileId, "bbb") } returns null
        coEvery { kidProfileDao.getProfileByIdOnce(profileId) } returns profile

        val result = (service.import("alter-9-11.json", profileId, applyPreset = false)
            as AppResult.Success).data

        assertThat(result.added).isEqualTo(1)
        assertThat(result.skipped).isEqualTo(1)
    }

    @Test
    fun `import uebernimmt die Ruhezeit nur auf Wunsch`() = runTest {
        coEvery { whitelistItemDao.findByYoutubeId(profileId, any()) } returns null
        coEvery { kidProfileDao.getProfileByIdOnce(profileId) } returns profile
        val updated = slot<KidProfileEntity>()
        coEvery { kidProfileDao.update(capture(updated)) } returns Unit

        val ohne = (service.import("alter-9-11.json", profileId, applyPreset = false)
            as AppResult.Success).data
        assertThat(ohne.presetApplied).isFalse()
        coVerify(exactly = 0) { kidProfileDao.update(any()) }

        val mit = (service.import("alter-9-11.json", profileId, applyPreset = true)
            as AppResult.Success).data
        assertThat(mit.presetApplied).isTrue()
        assertThat(updated.captured.bedtimeEnabled).isTrue()
        assertThat(updated.captured.bedtimeStartMinutes).isEqualTo(1200)
        assertThat(updated.captured.bedtimeEndMinutes).isEqualTo(390)
        assertThat(updated.captured.bedtimeWeekendOffsetMinutes).isEqualTo(60)
    }

    @Test
    fun `unlesbare Datei meldet einen Fehler statt abzustuerzen`() = runTest {
        source.files = mapOf("kaputt.json" to "{ das ist kein JSON")

        assertThat(service.availablePacks()).isEmpty()
        assertThat(service.import("kaputt.json", profileId, applyPreset = true))
            .isInstanceOf(AppResult.Error::class.java)
    }
}
