package com.soywiz.vitaorganizer.tasks

import com.soywiz.util.DumperModules
import com.soywiz.util.DumperNames
import com.soywiz.util.DumperNamesHelper
import com.soywiz.util.open2
import com.soywiz.vitaorganizer.*
import com.soywiz.vitaorganizer.ext.getBytes
import java.io.File
import java.util.zip.ZipFile

class UpdateFileListTask : VitaTask() {
	override fun perform() {
		synchronized(VitaOrganizer.VPK_GAME_IDS) {
			VitaOrganizer.VPK_GAME_IDS.clear()
		}
		status(Texts.format("STEP_ANALYZING_FILES", "folder" to VitaOrganizerSettings.vpkFolder))

		val MAX_SUBDIRECTORY_LEVELS = 2

		fun listVpkFiles(folder: File, level: Int = 0): List<File> {
			val out = arrayListOf<File>()
			if (level > MAX_SUBDIRECTORY_LEVELS) return out
			for (child in folder.listFiles()) {
				if (child.isDirectory) {
					out += listVpkFiles(child, level = level + 1)
				} else {
					if (child.extension.toLowerCase() == "vpk") out += child
				}
			}
			return out
		}

		val vpkFiles = listVpkFiles(File(VitaOrganizerSettings.vpkFolder))

		for ((index, vpkFile) in vpkFiles.withIndex()) {
			//println(vpkFile)
			status(Texts.format("STEP_ANALYZING_ITEM", "name" to vpkFile.name, "current" to index + 1, "total" to vpkFiles.size))
			try {
				ZipFile(vpkFile).use { zip ->
					val paramSfoData = zip.getBytes("sce_sys/param.sfo")

					val psf = PSF.read(paramSfoData.open2("r"))
					val gameId = psf["TITLE_ID"].toString()

					val entry = VitaOrganizerCache.entry(gameId)

					//try to find compressionlevel and vitaminversion or maiversion
					val paramsfo = zip.getEntry("sce_sys/param.sfo")
					val compressionLevel = if (paramsfo != null) paramsfo.method.toString() else ""

					var dumper = DumperNames.UNKNOWN
					for (file in DumperModules.values()) {
						val suprx = zip.getEntry(file.file)
						if (suprx != null) {
							dumper = DumperNamesHelper().findDumperBySize(suprx.size)
						}
					}

					println("For file ${vpkFile} Compressionslevel : ${compressionLevel} Dumperversion : ${dumper}")
					if (!entry.compressionFile.exists()) {
						entry.compressionFile.writeText(compressionLevel.toString())
					}
					if (!entry.dumperVersionFile.exists()) {
						entry.dumperVersionFile.writeText(dumper.shortName)
					}

					if (!entry.icon0File.exists()) {
						entry.icon0File.writeBytes(zip.getInputStream(zip.getEntry("sce_sys/icon0.png")).readBytes())
					}
					if (!entry.paramSfoFile.exists()) {
						entry.paramSfoFile.writeBytes(paramSfoData)
					}
					if (!entry.sizeFile.exists()) {
						val uncompressedSize = ZipFile(vpkFile).entries().toList().map { it.size }.sum()
						entry.sizeFile.writeText("" + uncompressedSize)
					}
					if (!entry.permissionsFile.exists()) {
						val ebootBinData = zip.getBytes("eboot.bin")
						entry.permissionsFile.writeText("" + EbootBin.hasExtendedPermissions(ebootBinData.open2("r")))
					}
					entry.pathFile.writeBytes(vpkFile.absolutePath.toByteArray(Charsets.UTF_8))
					synchronized(VitaOrganizer.VPK_GAME_IDS) {
						VitaOrganizer.VPK_GAME_IDS += gameId
					}
					//getGameEntryById(gameId).inPC = true
				}
			} catch (e: Throwable) {
				println("Error processing ${vpkFile.name}")
				e.printStackTrace()
			}
			//Thread.sleep(200L)
		}
		status(Texts.format("STEP_DONE"))
		VitaOrganizer.updateEntries()
	}
}