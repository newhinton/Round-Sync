package de.felixnuesse.rclone.commands

import de.felixnuesse.rclone.command.Command
import de.felixnuesse.rclone.result.ResultType


class Version: Command<String>("version") {



    override fun command(): ArrayList<String> {
        val params = arrayListOf<String>()
        params.add(this.command)
        params.add("--use-json-log")
        params.add("--log-level=INFO")
        return params
    }

    override fun parse(result: String): ResultType<String> {
        try {
            return ResultType.success(result)
        } catch (e: Exception) {
        }
        return ResultType.failure(tryToGetError(result))
    }


}