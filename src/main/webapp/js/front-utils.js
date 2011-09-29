function addMyEvent(el, type, myevent) {
    if (el.addEventListener){  
        el.addEventListener(type, myevent, false);   
    //                    testLi.addEventListener('click', myevent, false);   
    } else if (el.attachEvent){  
        el.attachEvent('on' + type, myevent);  
    }  
}

function frontInit() {
    var details = document.getElementById("details");
    var searchForm = document.getElementById("search-form");
    var listEls = [];        
    var tmpList = document.getElementsByTagName("li");
    for(var tmpI = 0, l = tmpList.length; tmpI < l; tmpI++) {
        if(tmpList[tmpI].getAttribute("name") === 'myonclick')
            listEls.push(tmpList[tmpI]);        
    }
        
    var intervalID = 0;
    var currentActive = 0;
    function createMyMouseOverEvent(el, elNumber, disable) {
        return function() {
            if(disable)
                clearInterval(intervalID);            
            
            currentActive = elNumber;
            details.innerHTML = '"' + el.jstitle + '"';            
            for(var jj = 0; jj < listEls.length; jj++) {
                var tmpLi = listEls[jj];            
                tmpLi.className = "";                        
            }
            el.className = "selected";            
            if(el.getAttribute("withsearch") === "true")
                searchForm.setAttribute("class", "front-search");
            else
                searchForm.setAttribute("class", "hidden");            
        };                
    }
    for(var ii = 0; ii < listEls.length; ii++) {                
        // closure + loops needs a separate create function
        var el = listEls[ii];
        // do not disturb with mouse over popup when javascript enabled
        el.jstitle = el.title;
        el.removeAttribute("title");        
        addMyEvent(el, 'mouseover', createMyMouseOverEvent(el, ii, true));
    // not necessary as we stop animation after the last item
    //        if(el.getAttribute("withsearch") === "true")
    //            addMyEvent(searchForm, 'mouseover', createMyMouseOverEvent(el, ii, true));
    }
    
    function switchDetails() {        
        // programmatically execute onMouseOver event but do NOT clearInterval
        createMyMouseOverEvent(listEls[currentActive], currentActive, false)();        
        currentActive++;
        if(currentActive >= listEls.length)
            clearInterval(intervalID);
    }
    intervalID = setInterval(switchDetails, 4000);
    switchDetails();
}

function createShowOnClick(el) {
    return function() {  
        if(el.style.display === 'none')
            el.style.display = 'block';
        else
            el.style.display = 'none';        
    };                
}
function jetslideInit() {
    var articles = document.getElementById("articles");
    var tmpList = articles.getElementsByTagName("div");
    for(var tmpI = 0, l = tmpList.length; tmpI < l; tmpI++) {
        if(tmpList[tmpI].getAttribute("class") === 'slide-max-share-text') {
            var maxShareDiv = tmpList[tmpI];
            var textDiv = maxShareDiv.nextSibling;
            while(textDiv != null) {
                if(textDiv.getAttribute && textDiv.getAttribute("class") === 'slide-text')
                    break;
                textDiv = textDiv.nextSibling;                
            }
            if(textDiv !== null)
                addMyEvent(textDiv, 'click', createShowOnClick(maxShareDiv));
        }                
    }
}
function fireEvent(element, event){
    if (document.createEventObject){
        // dispatch for IE
        var evt1 = document.createEventObject();
        return element.fireEvent('on'+event, evt1);
    } else {
        // dispatch for firefox + others
        var evt2 = document.createEvent("HTMLEvents");
        evt2.initEvent(event, true, true ); // event type,bubbling,cancelable
        return !element.dispatchEvent(evt2);
    }
}