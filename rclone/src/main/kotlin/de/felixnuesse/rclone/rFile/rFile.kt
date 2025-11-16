package de.felixnuesse.rclone.rFile

import de.felixnuesse.rclone.RemoteObject
import de.felixnuesse.rclone.commands.LsJson
import de.felixnuesse.rclone.result.LsJsonResultRow
import java.io.File

class rFile {

    var size = 0
    var isDir = false

    private lateinit var remote: RemoteObject


    constructor(remote: RemoteObject) {
        this.remote = RemoteObject.from(remote.name, remote.path)
    }

    constructor(parent: rFile, subdir: String): this(parent.remote) {
        if(remote.path.endsWith("/") || subdir.startsWith("/")) {
            remote.path += subdir
        } else {
            remote.path += "/$subdir"
        }
    }

    constructor(parent: rFile, result: LsJsonResultRow): this(parent, result.Name) {
        this.isDir = result.IsDir
        this.size = result.Size
    }

    fun listFiles(): ArrayList<rFile> {
        val list = arrayListOf<rFile>()
        val parent = this

        val result = LsJson(remote).exec()
        if(result.isSuccessful()) {
            result.getData().list.forEach {
                list.add(rFile(parent, it))
            }
        } else {
            System.err.println( "there was an error"+result.getError())
        }

        return list
    }

    fun getParent(): rFile {
        throw RuntimeException("Stub!")
    }

    fun getPath(): String? {
        throw RuntimeException("Stub!")
    }


    fun isDirectory(): Boolean {
        return isDir
    }

    fun isFile(): Boolean {
        return !isDir
    }


    fun mkdir(): Boolean {
        throw java.lang.RuntimeException("Stub!")
    }

    fun mkdirs(): Boolean {
        throw java.lang.RuntimeException("Stub!")
    }

    fun renameTo(dest: File): Boolean {
        throw java.lang.RuntimeException("Stub!")
    }

    fun inputStream() {}


    override fun toString(): String {
        return "rFile(remote=${remote.name}, path=${remote.path})"
    }


}