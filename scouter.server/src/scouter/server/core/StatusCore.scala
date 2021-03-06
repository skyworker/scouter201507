/*
*  Copyright 2015 LG CNS.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); 
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License. 
 *
 */
package scouter.server.core;
import scouter.lang.pack.StatusPack
import scouter.server.Logger
import scouter.server.core.cache.StatusCache
import scouter.server.db.StatusWR
import scouter.server.util.ThreadScala
import scouter.util.RequestQueue
import scouter.util.DateUtil
object StatusCore {
    val queue = new RequestQueue[StatusPack](CoreRun.MAX_QUE_SIZE);
    ThreadScala.startDaemon("scouter.server.core.StatusCore", { CoreRun.running }) {
        val p = queue.get();
        p.time = DateUtil.now
        
        StatusCache.put(p)
        StatusWR.add(p)
    }
    def add(p: StatusPack) {
        val ok = queue.put(p);
        if (ok == false) {
            Logger.println("S113", 10, "queue exceeded!!");
        }
    }
}
