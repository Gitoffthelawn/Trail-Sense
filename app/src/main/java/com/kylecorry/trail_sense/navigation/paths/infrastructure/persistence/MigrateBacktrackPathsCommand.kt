package com.kylecorry.trail_sense.navigation.paths.infrastructure.persistence

import com.kylecorry.trail_sense.navigation.paths.domain.IPathService
import com.kylecorry.trail_sense.navigation.paths.domain.Path
import com.kylecorry.trail_sense.navigation.paths.domain.PathMetadata
import com.kylecorry.trail_sense.shared.commands.CoroutineCommand

class MigrateBacktrackPathsCommand(
    private val pathService: IPathService,
    private val prefs: IPathPreferences
) : CoroutineCommand {

    override suspend fun execute() {
        val paths = pathService.getWaypoints()

        val style = prefs.defaultPathStyle

        pathService.endBacktrackPath()

        for (path in paths) {
            val newPath = pathService.addPath(Path(0, null, style, PathMetadata.empty, temporary = true))
            pathService.moveWaypointsToPath(path.value.map { it.copy(pathId = 0) }, newPath)
        }
    }

}