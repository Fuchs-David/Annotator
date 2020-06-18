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