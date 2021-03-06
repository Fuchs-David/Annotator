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

var numberOfResourcesForAnnotation = 10;
var currentResource = 0;
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
        let json = JSON.parse(getData.response);
        let triples = json.triples;
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
        data.getElementsByTagName("caption").item(0).innerHTML="<strong>You are currently annotating resource:</strong> " + triples[0].subject;
        try{
            for(var i=0;i<triples.length;i++){
                let triple = triples[i];
                let row = data.insertRow(-1);
                row.insertCell(0).innerText=triple.predicate;
                row.insertCell(1).innerText=triple.object;
            }
        }
        catch(Exception){
            console.warn("Inserting triples into table failed.");
        }
        finally{
            data.createTHead().innerHTML="<tr><th>DBpedia predicate</th><th>DBpedia object</th></tr>";
            if(data.getElementsByTagName("tbody").length === 0) data.appendChild(document.createElement("tbody"));
            mergeCells();
            window.scrollTo(0,0);
            document.getElementById("loading_icon").style.display = 'none';
            if(json.numberOfAnnotations >= 0){
                document.getElementById("numberOfAnnotations").innerText
                    = (numberOfAnnotations=json.numberOfAnnotations);
                document.getElementById("numberOfAnnotationsToSubmit").innerText
                    = window.sessionStorage.length;
                numberOfAnnotations+=window.sessionStorage.length;
            }
            if(typeof window.sessionStorage[currentResource] !== 'undefined')
                document.getElementById(JSON.parse(window.sessionStorage[currentResource]).type).checked = true;
            checkNumberOfAnnotations();
        }
    }
    else if(getData.readyState === XMLHttpRequest.DONE && getData.status !== 200){
        alert("Data could not have been retrieved from the server.\n\
Please try to request the data again.");
    }
};

sendData.onreadystatechange = function(){
    if(sendData.readyState === XMLHttpRequest.DONE && sendData.status === 201){
        alert("Data have been successfully uploaded to the server.");
        window.sessionStorage.clear();
        if(numberOfAnnotations >= 10)
            location.href = "s/thankyou";
        else
            location.reload(true);
    }
    else if(sendData.readyState === XMLHttpRequest.DONE && sendData.status !== 201){
        alert("Failed to upload data to the server.");
    }
};

function requestData(direction){
    if(direction === "forward" || direction === "backward"){
        dir = direction;
        getData.open("GET","data?direction=" + direction);
        getData.send();
        document.getElementById("loading_icon").style.display = 'block';
    }
}

function saveData(){
    let radioButtons = document.getElementById("radio_buttons").getElementsByTagName("input");
    let before = currentResource;
    let numberOfAnnotationsBefore = window.sessionStorage.length;
    for(let i=0; i<radioButtons.length;i++){
        let radioButton = radioButtons[i];
        if(radioButton.checked === true){
            let data = new Object();
            data.order = (dir==="forward" ? currentResource++ : currentResource--);
            data.type = radioButton.getAttribute("id");
            window.sessionStorage.setItem(data.order,JSON.stringify(data));
            radioButton.checked = false;
        }
    }
    if(numberOfAnnotationsBefore < window.sessionStorage.length){
        numberOfAnnotations++;
        checkNumberOfAnnotations();
    }
    if(before === currentResource){
        (dir==="forward" ? currentResource++ : currentResource--);
    }
}

function submitData(){
    saveData();
    let data = new Object();
    data.numberOfAnnotations = window.sessionStorage.length;
    data.annotations = new Array(window.sessionStorage.length);
    for(let i=0;i<window.sessionStorage.length;i++){
        data.annotations[i] = JSON.parse(window.sessionStorage.getItem(i));
    }
    sendData.open("POST","data");
    sendData.setRequestHeader("Content-Type", "application/json");
    sendData.send(JSON.stringify(data));
}

function mergeCells(){
    let data = document.getElementById("data");
    let dataBody = data.getElementsByTagName("tbody")[0];
    let dataRows = dataBody.getElementsByTagName("tr");
    let lastRow = null;
    let numberOfRows = 1;
    for(let i=0; i < dataRows.length; i++){
        if(lastRow !== null && dataRows[i].getElementsByTagName("td")[0].textContent === lastRow.getElementsByTagName("td")[0].textContent){
            dataRows[i].removeChild(dataRows[i].getElementsByTagName("td")[0]);
            lastRow.getElementsByTagName("td")[0].setAttribute("rowspan",++numberOfRows);
        }
        else{
            lastRow = dataRows[i];
            numberOfRows = 1;
        }
    }
}
window.onload = function(){
    mergeCells();
};

function checkNumberOfAnnotations(){
    if(numberOfResourcesForAnnotation < numberOfAnnotations)
        alert("You have annotated enough resources.\n" +
              "You can now submit them and feel free to close the window.");
}