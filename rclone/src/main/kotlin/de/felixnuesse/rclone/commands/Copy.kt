package de.felixnuesse.rclone.commands

import de.felixnuesse.rclone.RemoteObject
import de.felixnuesse.rclone.command.AsyncCallback
import de.felixnuesse.rclone.command.AsyncCommand


class Copy(var source: RemoteObject, var target: RemoteObject, private var callback: AsyncCallback?): AsyncCommand<String>("copy", callback) {

    companion object {
        private var bwlimit = ""

        fun setBandwithLimit(limit: String) {
            bwlimit = limit
        }
    }

    override fun command(): ArrayList<String> {

        val params = arrayListOf<String>()
        params.add(this.command)
        params.add(source.toString())
        params.add(target.toString())

        params.add("--use-json-log")
        params.add("--log-level=INFO")
        params.add("--stats-log-level=NOTICE")
        params.add("--stats=1s")

        if(bwlimit.isNotEmpty()) {
            params.add("--bwlimit=" + bwlimit)
        }
        return params
    }

    override fun isValidLine(line: String?): Boolean {
        if(line.equals("[")) return false
        if(line.equals("]")) return false
        return super.isValidLine(line)
    }

    override fun parse(result: String): String {
        return result
    }

}