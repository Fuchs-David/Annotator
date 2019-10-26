/* 
 * Copyright (C) 2019 David Fuchs
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
var sendData = new XMLHttpRequest();

sendData.onreadystatechange = new function(){
    if(sendData.readyState === XMLHttpRequest.DONE &&
            (sendData.status === 200 || sendData.status === 201)){
        alert("Authentication succeeded.");
    }
};
function submitCredentials(){
    let data = new Object();
    data.email = document.getElementById("email").value;
    data.password = document.getElementById("password").value;
    if((data.createAccount = document.getElementById("createAccount").checked)){
        data.repeatedPassword = document.getElementById("repeatedPassword").value;
        if(data.password !== data.repeatedPassword){
            alert("Password and repeated password are not the same.");
            return false;
        }
    }
    sendData.open("POST","auth");
    sendData.setRequestHeader("Content-Type", "application/json");
    sendData.send(JSON.stringify(data));
}