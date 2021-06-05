//yanchop's search engine integration code v2
package net.josephworks

import net.minecraft.client.Minecraft
import net.minecraft.network.play.client.C01PacketChatMessage
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.URL
import java.util.*

class YanchopUtil {
    /*
     * Previous integration code was pretty bad, cleaned it up, added some stuff, improved in general
     * Delete all previous search engine code when replacing it with this (also delete septhread)
     *
     * Some ideas for client devs:
     * yanchop killsults
     * autoYanchop module (yanchops random ppl on server automatically)
     * allow other people on the server to yanchop, by having them say the command in chat and you yanchopping for them
     *
     */
    //keep these public incase there is external stuff that uses the search engine, like killsults
    var mojangQueue = ArrayList<String>()
    var searchQueue = ArrayList<String>()
    var threadRunning = false
    var postMode = false
    var bypassFilter = true
    var key = ""

    //configure the code below to best suit your client, or just delete it
    fun yanchop(args: Array<String>) { //your command should call this function
        if (!threadRunning) {
            startThread()
            //attempt to load search key from file
        }
        if (args[0].startsWith("key=")) {
            key = args[0].substring(4)
            sendMsg("key updated")
            //fileutils save key in file, you do this part since every client has its own file utils
            return
        }
        if (key.length == 0) {
            //attempt to load key from file, if it cant, msg line below
            sendMsg("Error: key missing, please do key=(key)")
            return
        }

        //do not edit this
        if (args[0] == "post=false") {
            postMode = false
            sendMsg("Post mode set to false")
            return
        }
        if (args[0] == "post=true") {
            postMode = true
            sendMsg("Post mode set to true, you will now post the search results in public chat")
            return
        }
        if (args[0] == "bypass=true") {
            bypassFilter = true
            sendMsg("Chat bypass mode set to true")
            return
        }
        if (args[0] == "bypass=false") {
            bypassFilter = false
            sendMsg("Chat bypass mode set to false")
            return
        }
        if (args[0].startsWith("!check") || args[0].startsWith("!status")) {
            if (key.length > 8) checkStatus() else sendMsg("Please activate a key by doing key=(key)")
            return
        }


        //process search input
        if (args.size == 1) {
            mojangQueue.add(args[0])
            return
        }
        if (args.size == 2) {
            searchQueue.add(args[0] + ":" + args[1])
            return
        }
        sendMsg("Error: bad arguments")
    }

    //make this whatever command you want, like search, scan, dox, lookup, ect...
    val cmd: String
        get() = "yanchop" //make this whatever command you want, like search, scan, dox, lookup, ect...

    /** */ //The code below should NOT be edited
    //processes searches
    fun startThread() { //do not edit this function
        threadRunning = true
        Thread {
            while (true) {
                if (mojangQueue.size != 0) {
                    val req = mojangQueue[0]
                    mojangQueue.removeAt(0)
                    val uuid = IGNToUUID(req)
                    if (uuid == "ERROR_FAILED_IGN_TO_UUID") {
                        sendMsg("Error: failed ign to uuid")
                        continue
                    }
                    val pastIGNs = UUIDToPreviousIGNs(uuid)
                    if (pastIGNs == null) {
                        sendMsg("Error: failed uuid to ign list")
                        continue
                    }
                    var request = "player:$uuid"
                    for (ign in pastIGNs) {
                        if (request.length + ign.length < 7900) {
                            request += ",$ign"
                        } else {
                            sendMsg("Error: request too large, skipping: $ign")
                        }
                    }
                    execRequest(request, req)
                }
                if (searchQueue.size != 0) {
                    execRequest(searchQueue[0], searchQueue[0])
                    searchQueue.removeAt(0)
                }
                try {
                    Thread.sleep(5)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
        }.start()
    }

    //sends request to search engine
    fun execRequest(result: String, originalRequest: String) { //do not edit this function
        val link = "http://elgoog.ga/cgi-bin/se.fcgi?$key:$result"
        try {
            val url = URL(link)
            val connection = url.openConnection()
            connection.setRequestProperty("http.user_agent", "Bot")
            val reader = BufferedReader(InputStreamReader(url.openStream()))

            // TODO: fix postMode and DM update to Yanchop
            var line: String
            if (!postMode) {
                sendMsg("Search results for: $originalRequest")
                while (reader.readLine().also { line = it } != null) {
                    sendMsg(line)
                }
                sendMsg("")
            } else {
                var reply = originalRequest
                while (reader.readLine().also { line = it } != null) {
                    if (!line.contains("Finished in")) {
                        reply += "$line "
                        if (bypassFilter) { //edit this if you want, i think its sufficient
                            reply = reply.replace(".".toRegex(), "-")
                            reply = reply.replace("@".toRegex(), "AT")
                            reply.replace("ips[", "")
                            reply.replace("emails[", "")
                            reply.replace("forumNames[", "")
                            reply.replace("buycraftLogs[", "")
                        }
                    }
                }
                if (reply != originalRequest) Minecraft.getMinecraft().netHandler.addToSendQueue(
                    C01PacketChatMessage(
                        reply
                    )
                ) else sendMsg("Nothing found on $originalRequest")
            }
            reader.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun IGNToUUID(playername: String): String { //do not edit this function
        val link = "https://api.mojang.com/users/profiles/minecraft/$playername"
        return try {
            val url = URL(link)
            val connection = url.openConnection()
            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Macintosh; U; Intel Mac OS X 10.7; en-US; rv:1.9.2.2) Gecko/20100316 Firefox/3.6.2"
            )
            val reader = BufferedReader(InputStreamReader(url.openStream()))
            var page = ""
            var line: String
            while (reader.readLine().also { line = it } != null) {
                page = line
            }
            reader.close()
            if (page.contains("name")) {
                val length = playername.length
                page.substring(17 + length, page.length - 2)
            } else {
                "ERROR_FAILED_IGN_TO_UUID"
            }
        } catch (e: IOException) {
            e.printStackTrace()
            "ERROR_FAILED_IGN_TO_UUID"
        }
    }

    fun UUIDToPreviousIGNs(uuid: String): ArrayList<String> { //do not edit this function
        var page = ""
        val link = "https://api.mojang.com/user/profiles/$uuid/names"
        val names = ArrayList<String>()
        try {
            val url = URL(link)
            val connection = url.openConnection()
            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Macintosh; U; Intel Mac OS X 10.7; en-US; rv:1.9.2.2) Gecko/20100316 Firefox/3.6.2"
            )
            val reader = BufferedReader(InputStreamReader(url.openStream()))
            var line: String
            while (reader.readLine().also { line = it } != null) {
                page = line.substring(1, line.length - 1)
            }
            reader.close()
            val temp = page.split(",".toRegex()).toTypedArray()
            temp[0] = temp[0].replace("}", "")
            for (n in temp) {
                if (n.contains("name")) {
                    if (!names.contains(n.substring(9, n.length - 1))) {
                        names.add(n.substring(9, n.length - 1))
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return names
    }

    fun checkStatus() { //do not edit this function
        val link = "http://elgoog.ga/cgi-bin/se.fcgi?$key:status"
        try {
            val url = URL(link)
            val connection = url.openConnection()
            connection.setRequestProperty("http.user_agent", "Bot")
            val reader = BufferedReader(InputStreamReader(url.openStream()))
            var line = ""
            while (reader.readLine().also { line = it } != null) {
                if (!line.startsWith("#")) {
                    println(line)
                    val parts = line.split("\t".toRegex()).toTypedArray()
                    val vars = arrayOf("Initial days: ", "Requests left: ", "Expires in: ")
                    for (i in 1 until parts.size - 1) {
                        if (i == 3) {
                            //yeah it looks bad and yeah i dont care
                            val currentTime = System.currentTimeMillis() / 1000
                            val endTime = java.lang.Long.valueOf(parts[i])
                            val diff = endTime - currentTime
                            var minutes = (diff / 60).toInt()
                            var hours = minutes / 60
                            val days = hours / 24
                            minutes = minutes % 60
                            hours = hours % 60
                            sendMsg(vars[i - 1] + " " + days + " days, " + hours + " hours, " + minutes + " minutes")
                            continue
                        }
                        sendMsg(vars[i - 1] + " " + parts[i])
                    }
                }
            }
            reader.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        /*
     * Syntax: cmd term		OR		cmd type term
     *
     * "-yanchop notch" should pass in a single argument, "notch"
     * "-yanchop ign notch" should pass in two arguments, "ign" and "notch"
     *
     * if you do not pass in arguments properly, somehow... the code wont work!
     */
        /** */
        fun sendMsg(msg: String) {
            var msg = msg
            var n = 0
            while (n < msg.length) {
                if (msg[n] != ' ') {
                    val pre = msg.substring(0, n)
                    val end = msg.substring(n)
                    msg = "$pre§7$end"
                    n += 2
                }
                n++
            }
            //Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText("§7[§6" + Fan.fullname + "§7]§7 " + msg));
            Logger.ingameInfo(msg)
        }
    }
}
