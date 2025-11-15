package de.felixnuesse.rclone

class RemoteObject {

    var name = ""
    var path = "/"


    override fun toString(): String {
        if(name==""){
            return path
        }
        return "$name:$path"
    }

    fun setChild(child: String) {
        if(!path.endsWith("/")) {
            path += "/"
        }
        path += child
    }

    companion object {
        fun from(name: String, path: String): RemoteObject {
            val remote = RemoteObject()
            remote.path = path
            remote.name = name
            return remote
        }

        fun from(path: String): RemoteObject {
            val remote = RemoteObject()
            remote.path = path
            return remote
        }
    }

}