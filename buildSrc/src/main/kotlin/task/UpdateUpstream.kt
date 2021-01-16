package task

import ensureSuccess
import gitCmd
import org.apache.tools.ant.util.FileUtils
import org.gradle.api.Project
import org.gradle.api.Task
import rootProjectDir
import taskGroup
import toothpick
import upstreamDir
import upstreams
import Upstream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.charset.StandardCharsets
import java.util.stream.Collectors


internal fun Project.createUpdateUpstreamTask(
    receiver: Task.() -> Unit = {}
): Task = tasks.create("updateUpstream") {
    receiver(this)
    group = taskGroup
    doLast {
        ensureSuccess(gitCmd("fetch", dir = upstreamDir, printOut = true))
        ensureSuccess(gitCmd("reset", "--hard", toothpick.upstreamBranch, dir = upstreamDir, printOut = true))
        ensureSuccess(gitCmd("add", toothpick.upstream, dir = rootProjectDir, printOut = true))
        for (upstream in upstreams) {
            System.out.println(upstream.repoPath)
            ensureSuccess(gitCmd("fetch", dir = upstream.repoPath.toFile(), printOut = true))
            ensureSuccess(gitCmd("reset", "--hard", upstream.branch, dir = upstream.repoPath.toFile(), printOut = true))
            //ensureSuccess(gitCmd("add", "upstream/${upstream.name}", dir = upstream.repoPath.toFile(), printOut = true)) //TODO FIX
        }
        ensureSuccess(gitCmd("submodule", "update", "--init", "--recursive", dir = upstreamDir, printOut = true))
        for (upstream in upstreams) {
            val serverRepoPatches = upstream.getRepoServerPatches()
            val apiRepoPatches = upstream.getRepoAPIPatches()
            val serverPatches = upstream.serverList
            val apiPatches = upstream.apiList
            val fileUtils = FileUtils.getFileUtils()
            if (upstream.useBlackList) {
                if (serverRepoPatches != null) {
                    var i = 0
                    val currentPatchList = Path.of("${upstream.patchPath}/server").toFile().listFiles()
                    val tmpFolder = Path.of("${upstream.patchPath}/tmp/server").toFile()
                    tmpFolder.mkdirs()
                    for (patch in currentPatchList) {
                        fileUtils.copyFile("${upstream.patchPath}/server/${patch.name}",
                            "${upstream.patchPath}/tmp/server/${patch.name}")
                        patch.delete()
                    }
                    var currentPatchListFiltered = currentPatchList.toList().stream().sorted().map {patch -> patch.name.substring(5, patch.name.length)}.collect(Collectors.toList())
                    for (patch in serverRepoPatches) {
                        if (serverPatches != null && serverPatches.contains(patch)) {
                            continue
                        } else {
                            i++
                            updatePatch(fileUtils, upstream, serverRepoPatches, patch, i, "server", currentPatchListFiltered)
                        }
                    }
                    for (patch in tmpFolder.listFiles()) {
                        System.out.println(patch)
                        patch.delete()
                    }
                }
                if (apiRepoPatches != null) {
                    var i = 0
                    val currentPatchList = Path.of("${upstream.patchPath}/api").toFile().listFiles()
                    val tmpFolder = Path.of("${upstream.patchPath}/tmp/api").toFile()
                    tmpFolder.mkdirs()
                    for (patch in currentPatchList) {
                        fileUtils.copyFile("${upstream.patchPath}/api/${patch.name}",
                            "${upstream.patchPath}/tmp/api/${patch.name}")
                        patch.delete()
                    }
                    var currentPatchListFiltered = currentPatchList.toList().stream().sorted().map {patch -> patch.name.substring(5, patch.name.length)}.collect(Collectors.toList())
                    for (patch in apiRepoPatches) {
                        if (apiPatches != null && apiPatches.contains(patch)) {
                            continue
                        } else {
                            i++
                            updatePatch(fileUtils, upstream, apiRepoPatches, patch, i, "api", currentPatchListFiltered)
                        }
                    }
                    for (patch in tmpFolder.listFiles()) {
                        patch.delete()
                    }
                }
            } else {
                if (serverPatches != null) {
                    var i = 0
                    var currentPatchList = Path.of("${upstream.patchPath}/server").toFile().listFiles()
                    var tmpFolder = Path.of("${upstream.patchPath}/tmp/server").toFile()
                    tmpFolder.mkdirs()
                    for (patch in currentPatchList) {
                        fileUtils.copyFile("${upstream.patchPath}/server/${patch.name}",
                            "${upstream.patchPath}/tmp/server/${patch.name}")
                        patch.delete()
                    }
                    var currentPatchListFiltered = currentPatchList.toList().stream().sorted().map {patch -> patch.name.substring(5, patch.name.length)}.collect(Collectors.toList())
                    for (patch in serverPatches) {
                        if (serverRepoPatches != null && !serverRepoPatches.contains(patch)) {
                            continue
                        } else if (serverRepoPatches != null) {
                            i++
                            updatePatch(fileUtils, upstream, serverRepoPatches, patch, i, "server", currentPatchListFiltered)
                        }
                    }
                    for (patch in tmpFolder.listFiles()) {
                        patch.delete()
                    }
                }
                if (apiPatches != null) {
                    var i = 0
                    var currentPatchList = Path.of("${upstream.patchPath}/api").toFile().listFiles()
                    var tmpFolder = Path.of("${upstream.patchPath}/tmp/api").toFile()
                    tmpFolder.mkdirs()
                    for (patch in currentPatchList) {
                        fileUtils.copyFile("${upstream.patchPath}/api/${patch.name}",
                            "${upstream.patchPath}/tmp/api/${patch.name}")
                        patch.delete()
                    }
                    var currentPatchListFiltered = currentPatchList.toList().stream().sorted().map {patch -> patch.name.substring(5, patch.name.length)}.collect(Collectors.toList())
                    for (patch in apiPatches) {
                        if (apiRepoPatches != null && !apiRepoPatches.contains(patch)) {
                            continue
                        } else if (apiRepoPatches != null) {
                            i++
                            updatePatch(fileUtils, upstream, apiRepoPatches, patch, i, "api", currentPatchListFiltered)
                        }
                    }
                    for (patch in tmpFolder.listFiles()) {
                        patch.delete()
                    }
                }
            }
            upstream.updateUpstreamCommitHash()
        }
    }
}

private fun updatePatch(
    fileUtils: FileUtils,
    upstream: Upstream,
    serverRepoPatches: MutableList<String>,
    patch: String,
    i: Int,
    folder: String,
    currentPatchListFiltered: MutableList<String>
) {
    if (patchHasDiff(upstream, serverRepoPatches, patch, folder, currentPatchListFiltered)) {
        fileUtils.copyFile("${upstream.repoPath}/patches/$folder/" +
                "${String.format("%04d", serverRepoPatches.indexOf(patch) + 1)}-$patch",
            "${upstream.patchPath}/$folder/${String.format("%04d", i)}-$patch"
        )
    } else {
        fileUtils.copyFile("${upstream.patchPath}/tmp/$folder/" +
                "${String.format("%04d", currentPatchListFiltered.indexOf(patch) + 1)}-$patch",
            "${upstream.patchPath}/$folder/${String.format("%04d", i)}-$patch"
        )
    }
}

fun patchHasDiff(
    upstream: Upstream,
    serverRepoPatches: MutableList<String>,
    patch: String,
    folder: String,
    currentPatchListFiltered: MutableList<String>
): Boolean {
    if (!Path.of("${upstream.patchPath}/tmp/$folder/${String.format("%04d", currentPatchListFiltered.indexOf(patch) + 1)}-$patch").toFile().isFile) return true
    if (!patchChanged(upstream, serverRepoPatches, patch, folder)) return false
    val upstreamFile = Files.readAllLines(Path.of("${upstream.repoPath}/patches/$folder/${String.format("%04d", serverRepoPatches.indexOf(patch) + 1)}-$patch"), StandardCharsets.UTF_8)
    val repoFile = Files.readAllLines(Path.of("${upstream.patchPath}/tmp/$folder/${String.format("%04d", currentPatchListFiltered.indexOf(patch) + 1)}-$patch"), StandardCharsets.UTF_8)
    val lineListDiff = ArrayList<String>()
    upstreamFile.stream().filter {line -> line.startsWith("+") || line.startsWith("-")}
        .filter {line -> line.substring(1, line.length).trim().isNotBlank() }
        .forEach {line -> if (repoFile.contains(line)) {
            repoFile.remove(line)
        } else { lineListDiff.add(line) } }
    return lineListDiff.isNotEmpty()
}

fun patchChanged(
    upstream: Upstream,
    serverRepoPatches: MutableList<String>,
    patch: String,
    folder: String
): Boolean {
    val diffCheckCmdResult = upstream.project.gitCmd("diff", "--name-only", upstream.uptreamCommit, upstream.getCurrentCommitHash(), dir = upstream.repoPath.toFile() )
    val diffCheckResult = diffCheckCmdResult.output.toString()
    if (diffCheckResult.isBlank()) return false
    val diffCheckChangeFiles = diffCheckResult.split("\\n".toRegex()).toTypedArray().toList()
    return diffCheckChangeFiles.contains("patches/$folder/${String.format("%04d", serverRepoPatches.indexOf(patch) + 1)}-$patch")
}