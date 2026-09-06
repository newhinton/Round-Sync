package ca.pkay.rcloneexplorer.rclone

import android.util.Log
import de.schuelken.cloudbridge.extensions.tag
import org.json.JSONObject
import java.util.Objects

class ProviderOption {

    var name: String = ""
    var help: String = ""
    var provider: String = ""
    var default: String = ""
    var value: Objects? = null
    var examples: ArrayList<OptionExampleItem> = ArrayList()
    var shortOpt: String = ""
    var hide: Int = 0
    var required: Boolean = false
    var isPassword: Boolean = false
    var noPrefix: Boolean = false
    var advanced: Boolean = false
    var exclusive: Boolean = false
    var defaultStr: String = ""
    var valueStr: String = ""
    var type: String = ""

    companion object {
        fun newInstance(data: JSONObject): ProviderOption? {

            try {
                val item = ProviderOption()

                item.name = data.optString("Name")
                item.help = data.optString("Help")
                item.provider = data.optString("Type")
                item.default = data.optString("Default")
                //item.value = data.get("Value")
                item.shortOpt = data.optString("ShortOpt")
                item.hide = data.optInt("Hide")
                item.required = data.optBoolean("Required")
                item.isPassword = data.optBoolean("IsPassword")
                item.noPrefix = data.optBoolean("NoPrefix")
                item.advanced = data.optBoolean("Advanced")
                item.exclusive = data.optBoolean("Exclusive")
                item.defaultStr = data.optString("DefaultStr")
                item.valueStr = data.optString("ValueStr")
                item.type = data.optString("Type")

                val examples = data.optJSONArray("Examples")
                if (examples != null) {
                    for (i in 0 until examples.length()) {
                        item.examples.add(OptionExampleItem(
                            examples.getJSONObject(i).optString("Value"),
                            examples.getJSONObject(i).optString("Help"),
                            examples.getJSONObject(i).optString("Provider")
                        ))
                    }
                }

                return item
            } catch (e: Exception) {
                Log.e(tag(), data.toString(4))
            }

            return null
        }
    }


    fun getNameCapitalized(): String {
        var tempName = name
        var wasSpace = false
        var capitalized = tempName[0].uppercaseChar().toString()

        for(s in tempName.drop(1)){
            if(wasSpace){
                capitalized += s.uppercaseChar()
            } else {
                capitalized += if(s == '_') ' ' else s
            }
            wasSpace = s == '_'
        }

        return capitalized
    }


}