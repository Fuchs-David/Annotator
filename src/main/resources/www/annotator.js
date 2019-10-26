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

var numberOfAnnotations = 0;
var dir = "forward";

function escapeHTML(elementName,unsafeText) {
    let e = document.createElement(elementName);
    e.innerText = unsafeText;
    return e.innerHTML;
}

var getData = new XMLHttpRequest();
var notifyServerAboutFailure = new XMLHttpRequest();
var sendData = new XMLHttpRequest();

getData.onreadystatechange = function(){
    if(getData.readyState === XMLHttpRequest.DONE && getData.status === 200){
        let data = document.getElementById("data");
        let triples = JSON.parse(getData.response).triples;
        if(triples.length === 0){
            notifyServerAboutFailure.open("DELETE","data?numberOfTriples=" + triples.length);
            notifyServerAboutFailure.send();
            requestData(dir);
            return;
        }
        saveData();
        let thead = data.getElementsByTagName("thead").item(0);
        let tbody = data.getElementsByTagName("tbody").item(0);
        data.removeChild(thead);
        data.removeChild(tbody);
        data.getElementsByTagName("caption").item(0).innerHTML="<strong>You are currently annotating resource:</strong> &lt;" + triples[0].subject + "&gt;";
        try{
            for(var i=0;i<triples.length;i++){
                let triple = triples[i];
                let row = data.insertRow(-1);
                row.insertCell(0).innerHTML=triple.predicate;
                row.insertCell(1).innerHTML=triple.object;
            }
        }
        catch(Exception){
            console.warn("Inserting triples into table failed.");
        }
        finally{
            data.createTHead().innerHTML="<tr><th>DBpedia predicate</th><th>DBpedia object</th></tr>";
            if(data.getElementsByTagName("tbody").length === 0) data.appendChild(document.createElement("tbody"));
            window.scrollTo(0,0);
        }
    }
};

sendData.onreadystatechange = new function(){
    if(getData.readyState === XMLHttpRequest.DONE && getData.status === 201){
        alert("Data have been successfully uploaded to the server.");
    }
};

function requestData(direction){
    if(direction === "forward" || direction === "backward"){
        dir = direction;
        getData.open("GET","data?direction=" + direction);
        getData.send();
    }
}

function saveData(){
    let radioButtons = document.getElementById("radio_buttons").getElementsByTagName("input");
    for(let i=0; i<radioButtons.length;i++){
        radioButton = radioButtons[i];
        if(radioButton.checked === true){
            let data = new Object();
            data.order = numberOfAnnotations++;
            data.type = radioButton.getAttribute("id");
            window.sessionStorage.setItem(data.order,JSON.stringify(data));
            radioButton.checked = false;
        }
    }
}

function submitData(){
    saveData();
    let data = new Object();
    data.numberOfAnnotations = numberOfAnnotations;
    data.annotations = new Array(numberOfAnnotations+1);
    for(let i=0;i<numberOfAnnotations;i++){
        data.annotations[i] = JSON.parse(window.sessionStorage.getItem(i));
    }
    sendData.open("POST","data");
    sendData.setRequestHeader("Content-Type", "application/json");
    sendData.send(JSON.stringify(data));
    window.sessionStorage.clear();
    location.reload(true);
}