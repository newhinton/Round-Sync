package de.felixnuesse.rclone.commands

import de.felixnuesse.rclone.RemoteObject
import de.felixnuesse.rclone.command.SyncCommand
import de.felixnuesse.rclone.result.LsJsonResult
import de.felixnuesse.rclone.result.ResultType


class LsJson(var source: RemoteObject): SyncCommand<LsJsonResult>("lsjson") {

    companion object {
        fun from(name: String, path: String): LsJson {
            val remote = RemoteObject()
            remote.name = name
            remote.path = path
            return LsJson(remote)
        }
    }

    override fun command(): ArrayList<String> {
        val params = arrayListOf<String>()
        params.add(this.command)
        params.add(source.toString())
        params.add("--use-json-log")
        params.add("--log-level=INFO")
        return params
    }

    override fun parse(result: String): ResultType<LsJsonResult> {
        try {
            return ResultType.success(LsJsonResult.fromResultRowList(result))
        } catch (e: Exception) {
        }
        return ResultType.failure(tryToGetError(result))
    }


}