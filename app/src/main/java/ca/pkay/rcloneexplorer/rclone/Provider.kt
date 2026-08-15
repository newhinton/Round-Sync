package ca.pkay.rcloneexplorer.rclone

import org.json.JSONObject

class Provider(val name: String) {

    val options = ArrayList<ProviderOption>()
    var description = ""
    var prefix = ""
    var commandHelp = ""


    companion object {
        fun newInstance(data: JSONObject): Provider {
            val item = Provider(data.optString("Name"))
            item.description = data.optString("Description")
            item.prefix = data.optString("Prefix")
            item.commandHelp = data.optString("CommandHelp")

            val options = data.optJSONArray("Options")
            if (options != null) {
                for (i in 0 until options.length()) {
                    ProviderOption.newInstance(options.getJSONObject(i))?.let { item.options.add(it) }
                }
            }

            return item
        }
    }


    /**
     * For ease of use, this usually applies title-capitalization.
     * However, some special cases are changed differently, for ease of use.
     * One example is ftp: it will be changed to FTP.
     */
    fun getNameCapitalized(): String {

        when(name) {
            "ftp" -> return "FTP"
            "hdfs" -> return "HDFS"
            "hidrive" -> return "HiDrive"
            "sftp" -> return "SFTP"
            "smb" -> return "SMB"
            "http" -> return "HTTP"
        }

        var tempName = name
        var wasSpace = false
        var capitalized = tempName[0].uppercaseChar().toString()

        for(s in tempName.drop(1)){
            if(wasSpace){
                capitalized += s.uppercaseChar()
            } else {
                capitalized += s
            }
            wasSpace = s == ' '
        }

        return capitalized
    }

}