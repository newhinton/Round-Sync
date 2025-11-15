package de.felixnuesse.rclone

class Rclone {

    companion object {

        private var config = ""
        private var base = "rclone"

        fun getRcloneBase(): ArrayList<String> {
            val rclone = arrayListOf<String>()
            rclone.add(base)

            if(config.isNotEmpty()) {
                rclone.add("--config=" + config)
            }

            return rclone
        }

        fun setConfig(path: String) {
           config = path
        }

        fun setBase(path: String) {
            base = path
        }
    }
}