/* Randy TV for Samsung Tizen TV. No dependencies / no credentials in source. ES5 for older TV engines. */
(function () {
  "use strict";
  var $ = function (id) { return document.getElementById(id); };
  var state = {section:"home",category:"all",query:"",page:0,pageSize:48,items:[],categories:[],live:[],vod:[],series:[],liveCats:[],vodCats:[],seriesCats:[],favorites:[],detail:null,seriesInfo:null,playing:null,playlist:[],playlistIndex:0,history:[],credentials:null,activeKey:null,remoteDigits:"",loadToken:0};
  var store = {read:function(k,fall){try {var v=localStorage.getItem(k);return v===null?fall:JSON.parse(v);}catch(e){return fall;}},write:function(k,v){try{localStorage.setItem(k,JSON.stringify(v));}catch(e){}},remove:function(k){try{localStorage.removeItem(k);}catch(e){}}};
  state.favorites=store.read("randy_tizen_favorites",[]);
  state.history=store.read("randy_tizen_history",[]);
  var playerMode="", overlayTimer=null, digitTimer=null, progressTimer=null, activeXhr=[], toastTimer=null, zapSwitchTimer=null, zapHideTimer=null, playSerial=0, trackMode="", subtitleOff=!!store.read("randy_tizen_subtitles_off",false);
  var guideOpen=false, guideIndex=0, guideColumn=0, guideToken=0, epgCache={}, epgCacheOrder=[], epgPending={};
  function safe(s){return String(s===null||s===undefined?"":s).replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;").replace(/"/g,"&quot;").replace(/'/g,"&#39;");}
  function digits(s){return /^\d+$/.test(String(s||""));}
  function adult(s){return /(xxx|adult|porno|porn\b|erotic|playboy|penthouse|brazzers|18\+|desnud|hustler|sex\b)/i.test(String(s||""));}
  function good(item){return item&&!adult(item.name||item.title||"");}
  function baseURL(url){return String(url||"").trim().replace(/\/+$/,"");}
  function encode(v){return encodeURIComponent(String(v));}
  function credentials(){return state.credentials;}
  function getURL(action,id){var c=credentials();return c.server+"/player_api.php?username="+encode(c.username)+"&password="+encode(c.password)+"&action="+encode(action)+(id===undefined?"":"&series_id="+encode(id));}
  function mediaURL(kind,item){var c=credentials(), ext=String(item.container_extension||"mp4").replace(/[^a-zA-Z0-9]/g,"")||"mp4";var id=kind==="series"?item.id:item.stream_id;return c.server+"/"+(kind==="live"?"live":kind==="vod"?"movie":"series")+"/"+encode(c.username)+"/"+encode(c.password)+"/"+encode(id)+"."+(kind==="live"?"m3u8":ext);}
  function notify(msg){var e=$("toast");e.textContent=msg;e.className="toast";clearTimeout(toastTimer);toastTimer=setTimeout(function(){e.className="toast hidden";},4200);}
  function showView(view){["setup","main","player"].forEach(function(x){$(x).className="view"+(view===x?"":" hidden");});}
  function request(url,callback){var xhr=new XMLHttpRequest();xhr.open("GET",url,true);xhr.timeout=20000;activeXhr.push(xhr);xhr.onreadystatechange=function(){if(xhr.readyState===4){var i=activeXhr.indexOf(xhr);if(i!==-1)activeXhr.splice(i,1);if(xhr.status>=200&&xhr.status<300){try {callback(null,JSON.parse(xhr.responseText));}catch(e){callback("El servidor devolvió una respuesta no válida.");}}else{callback("No se pudo conectar al servidor. Revisa la dirección, la red y la compatibilidad del servicio (CORS).");}}};xhr.onerror=function(){callback("Error de conexión. Comprueba la red y los permisos del servidor.");};xhr.ontimeout=function(){callback("El servidor tardó demasiado en responder.");};try{xhr.send();}catch(e){callback("No fue posible solicitar la lista.");}}
  function load(kind,done){request(getURL(kind),function(err,data){done(err,Array.isArray(data)?data:[]);});}
  function setLoginError(msg){$("login-error").textContent=msg||"";}
  function startLogin(ev){if(ev&&ev.preventDefault)ev.preventDefault();var c={server:baseURL($("server").value),username:$("username").value.trim(),password:$("password").value};if(!/^https?:\/\/[^\/\s]+/i.test(c.server)||!c.username||!c.password){setLoginError("Completa dirección del servidor, usuario y contraseña.");return;}state.credentials=c;setLoginError("Conectando al servicio…");$("connect").disabled=true;request(getURL("get_live_categories"),function(err,data){$("connect").disabled=false;if(err||!Array.isArray(data)){setLoginError(err||"No se pudo validar esta cuenta.");return;}state.liveCats=data.filter(function(x){return !adult(x.category_name);});if($("remember").checked)store.write("randy_tizen_login",c);else store.remove("randy_tizen_login");setLoginError("");launch();});}
  function launch(){showView("main");selectSection("home");loadCatalog();setTimeout(function(){focusFirst();},80);}
  function refreshCatalogView(){
    if(state.detail || $("main").className.indexOf("hidden")!==-1)return;
    var active=document.activeElement,inside=active&&$("content").contains(active);
    var type="",value="",ordinal=0;
    if(inside){
      if(active.id==="search")type="search";
      else if(active.hasAttribute("data-section")){
        type="section";value=active.getAttribute("data-section");
        var oldBtns=$("content").querySelectorAll("[data-section]");
        for(var x=0;x<oldBtns.length;x++)if(oldBtns[x]===active){
          for(var z=0;z<x;z++)if(oldBtns[z].getAttribute("data-section")===value)ordinal++;
          break;
        }
      }else if(active.hasAttribute("data-home-live")){type="live";value=active.getAttribute("data-home-live");}
      else if(active.hasAttribute("data-home-resume")){type="resume";value=active.getAttribute("data-home-resume");}
      else if(active.hasAttribute("data-item-key")){type="item";value=active.getAttribute("data-item-key");}
      else if(active.className&&String(active.className).indexOf("category")!==-1){
        type="category";value=active.textContent;
      }
    }
    render();
    if(!inside)return;
    var target=null,btns,i,count=0;
    if(type==="search")target=$("search");
    else if(type==="section"){
      btns=$("content").querySelectorAll("[data-section]");
      for(i=0;i<btns.length;i++)if(btns[i].getAttribute("data-section")===value){
        if(count===ordinal){target=btns[i];break;}
        count++;
      }
    }else if(type==="live"||type==="resume"){
      btns=$("content").querySelectorAll(type==="live"?"[data-home-live]":"[data-home-resume]");
      for(i=0;i<btns.length;i++)if(btns[i].getAttribute(type==="live"?"data-home-live":"data-home-resume")===value){target=btns[i];break;}
    }else if(type==="item"){
      btns=$("content").querySelectorAll("[data-item-key]");
      for(i=0;i<btns.length;i++)if(btns[i].getAttribute("data-item-key")===value){target=btns[i];break;}
    }else if(type==="category"){
      btns=$("content").querySelectorAll(".category");
      for(i=0;i<btns.length;i++)if(btns[i].textContent===value){target=btns[i];break;}
    }
    if(target)target.focus();
  }
  function loadCatalog(){var token=++state.loadToken;load("get_live_streams",function(err,items){if(token!==state.loadToken)return;if(err){notify("No se pudieron cargar los canales.");}else{state.live=items.filter(good);if(state.section==="live"||state.section==="favorites"||state.section==="home")refreshCatalogView();}});load("get_live_categories",function(err,items){if(token!==state.loadToken)return;if(!err){state.liveCats=items.filter(function(x){return !adult(x.category_name);});if(state.section==="live")refreshCatalogView();}});setTimeout(function(){if(token!==state.loadToken)return;load("get_vod_categories",function(e,x){if(!e)state.vodCats=x.filter(function(i){return !adult(i.category_name);});});load("get_series_categories",function(e,x){if(!e)state.seriesCats=x.filter(function(i){return !adult(i.category_name);});});load("get_vod_streams",function(e,x){if(!e){state.vod=x.filter(good);if(state.section==="vod")refreshCatalogView();}});load("get_series",function(e,x){if(!e){state.series=x.filter(good);if(state.section==="series")refreshCatalogView();}});},300);}
  function typeOf(item){return item.series_id!==undefined?"series":item.stream_type==="movie"||item.container_extension!==undefined?"vod":"live";}
  function itemId(item,type){return type==="series"?(item.series_id!==undefined?item.series_id:item.id):item.stream_id;}
  function itemKey(type,item){return type+":"+itemId(item,type);}
  function isFav(type,item){return state.favorites.indexOf(itemKey(type,item))!==-1;}
  function toggleFav(type,item){var k=itemKey(type,item),idx=state.favorites.indexOf(k);if(idx!==-1)state.favorites.splice(idx,1);else state.favorites.push(k);store.write("randy_tizen_favorites",state.favorites);notify(idx!==-1?"Quitado de favoritos":"Agregado a favoritos");if($("player").className.indexOf("hidden")===-1)updatePlayerFav();else render();}
  function allItems(type){return type==="live"?state.live:type==="vod"?state.vod:state.series;}
  function favItems(){var out=[];state.favorites.forEach(function(k){var p=k.split(":"),list=allItems(p[0]);for(var i=0;i<list.length;i++){if(String(itemId(list[i],p[0]))===p[1]){out.push({type:p[0],item:list[i]});break;}}});return out;}
  function sectionLabel(section){return section==="home"?"Inicio":section==="live"?"TV en vivo":section==="vod"?"Películas":section==="series"?"Series":"Favoritos";}
  function selectSection(section){
    stopPlayerIfOpen();
    state.section=section;state.category="all";state.query="";state.page=0;state.detail=null;state.seriesInfo=null;
    showView("main");
    $("top-title").textContent=sectionLabel(section);
    var tabs=document.querySelectorAll(".tab[data-nav]");
    for(var i=0;i<tabs.length;i++){
      var selected=tabs[i].getAttribute("data-nav")===section;
      tabs[i].className="tab focusable"+(selected?" selected":"");
      tabs[i].setAttribute("aria-current",selected?"page":"false");
    }
    render();$("content").scrollTop=0;focusFirst();
  }
  function focusFirst(){var first=document.querySelector("#content .focusable")||$("go-home");if(first)first.focus();}
  function render(){
    if(state.detail){renderDetail();return;}
    if(state.section==="home"){renderHome();return;}
    var label=sectionLabel(state.section);
    var h='<div class="section-top"><div><span class="eyebrow">TU BIBLIOTECA</span><h1>'+label+'</h1></div><span class="count" id="result-count">Cargando…</span></div>'+
      '<div class="toolbar"><input class="search-input focusable" id="search" placeholder="⌕  Buscar por nombre" aria-label="Buscar '+label+'" type="search"/></div>'+
      '<div class="categories" id="cats" aria-label="Filtrar por categoría"></div>'+
      '<div class="media-grid'+(state.section==="vod"||state.section==="series"?" poster-grid":state.section==="live"?" live-grid":"")+'" id="media-grid"></div>'+
      '<div class="more-holder" id="more-holder"></div>';
    $("content").innerHTML=h;
    $("search").value=state.query;
    $("search").addEventListener("input",function(){state.query=this.value;state.page=0;renderResults();});
    renderCategories();renderResults();
  }
  function renderHome(){
    var h='<div class="home-hero"><div class="hero-copy">'+
      '<span class="eyebrow">●  BIENVENIDO A RANDY TV</span>'+
      '<h1>Todo lo que te gusta,<br/>en un solo lugar<span>.</span></h1>'+
      '<p>Tu televisión, películas y series favoritas. Sin menús complicados, solo disfruta.</p>'+
      '<div class="hero-actions">'+
      '<button class="primary focusable" data-section="live" type="button">▶ &nbsp; Ver TV en vivo</button>'+
      '<button class="ghost-button focusable" data-section="favorites" type="button">☆ &nbsp; Mis favoritos</button>'+
      '</div></div>'+
      '<div class="hero-art" aria-hidden="true"><div class="hero-screen"><strong>R</strong><small>RANDY TV</small></div><span class="art-chip">● EN VIVO</span></div></div>'+
      '<div class="home-head"><span class="eyebrow">ACCESOS RÁPIDOS</span><h2>¿Qué te gustaría ver?</h2></div>'+
      '<div class="home-tiles">'+
      '<button class="home-tile tile-live focusable" data-section="live" type="button"><span class="tile-icon">▣</span><strong>TV en vivo</strong><small>Todos tus canales</small></button>'+
      '<button class="home-tile tile-vod focusable" data-section="vod" type="button"><span class="tile-icon">▶</span><strong>Películas</strong><small>Para todos los gustos</small></button>'+
      '<button class="home-tile tile-series focusable" data-section="series" type="button"><span class="tile-icon">▤</span><strong>Series</strong><small>Temporadas y episodios</small></button>'+
      '<button class="home-tile tile-favorites focusable" data-section="favorites" type="button"><span class="tile-icon">☆</span><strong>Favoritos</strong><small>Lo que más disfrutas</small></button>'+
      '</div>';
    var recent=[],seen={};
    for(var j=0;j<state.history.length&&recent.length<4;j++){
      var entry=state.history[j],key=entry.key||"",parts=key.split(":");
      if(seen[key]||entry.position<30)continue;
      if(parts[0]==="series"&&entry.media&&entry.media.id){
        recent.push({type:"series",item:entry.media,name:entry.name||entry.media.name||"Episodio"});
        seen[key]=true;
      }else if(parts[0]==="vod"){
        for(var k=0;k<state.vod.length;k++){
          if(String(state.vod[k].stream_id)===parts[1]){
            recent.push({type:"vod",item:state.vod[k],name:state.vod[k].name});
            seen[key]=true;break;
          }
        }
      }
    }
    if(recent.length){
      h+='<div class="home-head"><span class="eyebrow">A TU RITMO</span><h2>Continuar viendo</h2></div><div class="continue-list">';
      for(var n=0;n<recent.length;n++){
        h+='<button type="button" class="continue-card focusable" data-home-resume="'+n+'"><strong>▶ '+safe(recent[n].name)+'</strong><small>'+(recent[n].type==="series"?"Retomar capítulo":"Retomar película")+'</small></button>';
      }
      h+='</div>';
    }
    if(state.live.length){
      h+='<div class="live-preview"><div class="home-head section-heading"><div><span class="eyebrow">SIEMPRE CONTIGO</span><h2>En vivo ahora</h2></div><small>Elige un canal con OK</small></div><div class="preview-grid">';
      for(var i=0;i<Math.min(5,state.live.length);i++){
        var it=state.live[i],img=picture(it,"live");
        h+='<button type="button" class="preview-card focusable" data-home-live="'+i+'">';
        h+=/^https?:\/\//i.test(img)?'<img src="'+safe(img)+'" alt=""/>':'<span class="preview-fallback">▣</span>';
        h+='<strong>'+safe(it.name)+'</strong></button>';
      }
      h+='</div></div>';
    }
    $("content").innerHTML=h;
    var buttons=$("content").querySelectorAll("[data-section]");
    for(var b=0;b<buttons.length;b++)buttons[b].onclick=function(){selectSection(this.getAttribute("data-section"));};
    var liveBtns=$("content").querySelectorAll("[data-home-live]");
    for(var l=0;l<liveBtns.length;l++)liveBtns[l].onclick=function(){
      var index=Number(this.getAttribute("data-home-live")),it=state.live[index];
      if(!it)return;
      var list=state.live.map(function(item){return {type:"live",item:item};});
      openItem("live",it,list);
    };
    var resumeBtns=$("content").querySelectorAll("[data-home-resume]");
    for(var x=0;x<resumeBtns.length;x++)resumeBtns[x].onclick=function(){
      var index=Number(this.getAttribute("data-home-resume"));
      if(recent[index])play(recent[index].type,recent[index].item);
    };
    var homeImages=$("content").querySelectorAll(".preview-card img");
    for(var z=0;z<homeImages.length;z++)homeImages[z].onerror=function(){this.style.display="none";};
  }
  function categories(){return state.section==="live"?state.liveCats:state.section==="vod"?state.vodCats:state.seriesCats;}
  function renderCategories(){
    var el=$("cats");if(!el)return;el.innerHTML="";
    if(state.section==="favorites")return;
    var cs=[{category_id:"all",category_name:"Todos"}];
    if(state.section==="live")cs.push({category_id:"24h",category_name:"24 horas"},{category_id:"light",category_name:"Señal ligera"},{category_id:"simpsons",category_name:"Los Simpson"});
    cs=cs.concat(categories());
    cs.forEach(function(c){
      var b=document.createElement("button");
      b.type="button";b.className="category focusable"+(state.category===String(c.category_id)?" selected":"");
      b.textContent=c.category_name;
      b.setAttribute("aria-pressed",state.category===String(c.category_id)?"true":"false");
      b.onclick=function(){
        state.category=String(c.category_id);state.page=0;
        renderCategories();renderResults();
        var selected=el.querySelector(".category.selected");if(selected)selected.focus();
      };
      el.appendChild(b);
    });
  }
  function getFiltered(){var q=state.query.toLowerCase().trim(),arr=[];if(state.section==="favorites")arr=favItems();else arr=allItems(state.section).map(function(i){return {type:state.section,item:i};});
    if(state.category!=="all"){arr=arr.filter(function(w){var c=state.category,n=String(w.item.name||"").toLowerCase();if(c==="24h")return /24[\s\/-]?(h|horas|7)/i.test(n);if(c==="simpsons")return n.indexOf("simpson")!==-1;if(c==="light")return /2\s?mb|\|\s?2\b|\|\s?sd\b|\bsd\b/i.test(n);return String(w.item.category_id)===c;});}
    if(q)arr=arr.filter(function(w){return String(w.item.name||"").toLowerCase().indexOf(q)!==-1;});
    return arr;
  }
  function picture(item,type){return item.stream_icon||item.cover||(item.info&&item.info.movie_image)||"";}
  var imageObserver=null;
  function hydrateThumbs(el){
    if(imageObserver){imageObserver.disconnect();imageObserver=null;}
    var images=el.querySelectorAll("img[data-src]");
    function loadImg(img){
      var src=img.getAttribute("data-src");
      if(!/^https?:\/\//i.test(src))return;
      img.onerror=function(){this.style.display="none";};
      img.src=src;img.removeAttribute("data-src");
    }
    if("IntersectionObserver" in window){
      imageObserver=new IntersectionObserver(function(entries,obs){
        for(var i=0;i<entries.length;i++)if(entries[i].isIntersecting){loadImg(entries[i].target);obs.unobserve(entries[i].target);}
      },{root:$("content"),rootMargin:"220px 0px"});
      for(var j=0;j<images.length;j++)imageObserver.observe(images[j]);
    }else{
      for(var k=0;k<images.length;k++)loadImg(images[k]);
    }
  }
  function renderResults(){
    var el=$("media-grid"),more=$("more-holder");if(!el)return;
    var list=getFiltered(),count=$("result-count");
    count.textContent=list.length+" disponibles";
    var slice=list.slice(0,(state.page+1)*state.pageSize);
    el.innerHTML="";
    if(!list.length){
      el.innerHTML='<div class="empty">'+
        (state.section==="favorites"?"Todavía no tienes favoritos. Abre un contenido y pulsa ☆ Favorito.":
        state.query?"No encontramos resultados para esa búsqueda.":"Todavía no hay contenido disponible. Si acabas de entrar, espera a que termine la carga.")+
        '</div>';
    }
    var fragment=document.createDocumentFragment();
    slice.forEach(function(w,idx){
      var it=w.item,ty=w.type,b=document.createElement("button");
      b.type="button";b.className="media-card focusable"+(ty==="live"?" live-tile":" poster");
      b.setAttribute("aria-label",it.name||"Abrir contenido");
      b.setAttribute("data-item-key",itemKey(ty,it));
      var src=picture(it,ty),img=/^https?:\/\//i.test(src)?'<img alt="" data-src="'+safe(src)+'"/>':'<span class="card-placeholder">'+(ty==="live"?"▣":"▶")+'</span>';
      if(ty==="live"){
        b.innerHTML='<span class="logo-pad">'+img+'</span><span class="text-content">'+
          '<span class="live-number">'+safe(it.num||idx+1)+'</span><strong>'+safe(it.name)+'</strong>'+
          '<small>● EN VIVO'+(isFav(ty,it)?" · ★":"")+'</small></span>';
      }else{
        b.innerHTML=img+'<span class="text-content"><strong>'+safe(it.name)+'</strong><small>'+
          (ty==="vod"?"PELÍCULA":"SERIE")+
          (isFav(ty,it)?' <span class="star">★</span>':"")+'</small></span>';
      }
      b.onclick=function(){openItem(ty,it,list);};
      fragment.appendChild(b);
    });
    el.appendChild(fragment);
    hydrateThumbs(el);
    more.innerHTML="";
    if(list.length>slice.length){
      var b=document.createElement("button");
      b.className="more-button focusable";b.type="button";
      b.textContent="Ver más contenido · "+(list.length-slice.length)+" restantes";
      b.onclick=function(){
        state.page++;renderResults();
        var items=$("media-grid").querySelectorAll(".media-card");
        if(items.length)items[Math.min(state.page*state.pageSize,items.length-1)].focus();
      };
      more.appendChild(b);
    }
  }
  function openItem(type,item,list){if(type==="live"){state.playlist=list.filter(function(w){return w.type==="live";}).map(function(w){return w.item;});state.playlistIndex=0;for(var li=0;li<state.playlist.length;li++){if(String(state.playlist[li].stream_id)===String(item.stream_id)){state.playlistIndex=li;break;}}play("live",item);return;}
    state.detail={type:type,item:item};state.seriesInfo=null;render();focusFirst();if(type==="series"){var id=String(item.series_id),current=state.detail;request(getURL("get_series_info",id),function(err,data){if(!err&&state.detail===current){state.seriesInfo=data;renderDetail();}});}
  }
  function renderDetail(){
    var d=state.detail;if(!d)return;
    var i=d.item,t=d.type,img=picture(i,t);
    var poster=/^https?:\/\//i.test(img)?'<img class="detail-poster" alt="" src="'+safe(img)+'"/>':'<div class="detail-poster"></div>';
    var plot=safe(i.plot||(state.seriesInfo&&state.seriesInfo.info&&state.seriesInfo.info.plot)||"Elige reproducir para disfrutar este contenido.");
    var h='<div class="detail-layout">'+poster+'<div class="detail-copy">'+
      '<span class="eyebrow">'+(t==="vod"?"PELÍCULA":"SERIE")+' · RANDY TV</span>'+
      '<h1>'+safe(i.name)+'</h1><p>'+plot+'</p><div class="detail-actions">'+
      '<button id="play-detail" class="primary focusable" type="button">'+(t==="vod"?"▶ Reproducir":"Ver capítulos")+'</button>'+
      '<button id="fav-detail" class="secondary-action focusable" type="button">'+(isFav(t,i)?"★ Quitar favorito":"☆ Agregar a favoritos")+'</button>'+
      '<button id="back-detail" class="secondary-action focusable" type="button">← Volver</button>'+
      '</div></div></div><div id="episodes"></div>';
    $("content").innerHTML=h;
    $("back-detail").onclick=closeDetail;
    $("fav-detail").onclick=function(){toggleFav(t,i);renderDetail();$("fav-detail").focus();};
    if(t==="vod"){
      $("play-detail").onclick=function(){play("vod",i);};
      var hist=findHistory(t,i);if(hist&&hist.position>30)$("play-detail").textContent="▶ Continuar película";
    }else{
      $("play-detail").onclick=function(){
        var b=$("episodes").querySelector(".episode-button");if(b)b.focus();else notify("Cargando capítulos…");
      };
      renderEpisodes();
    }
  }
  function renderEpisodes(){if(!state.seriesInfo||!state.detail)return;var eps=state.seriesInfo.episodes||{},keys=Object.keys(eps).sort(function(a,b){return Number(a)-Number(b);}),el=$("episodes");if(!keys.length){el.innerHTML='<div class="empty">No hay capítulos disponibles o aún se están cargando.</div>';return;}var html="";keys.forEach(function(k){html+='<h2>Temporada '+safe(k)+'</h2><div class="episode-list">';(eps[k]||[]).forEach(function(ep){var epName=ep.title||"Capítulo "+(ep.episode_num||"");html+='<button class="episode-button focusable" data-episode="'+safe(ep.id)+'" data-season="'+safe(k)+'">▶ '+safe(ep.episode_num||"")+" · "+safe(epName)+'</button>';});html+='</div>';});el.innerHTML=html;var buttons=el.querySelectorAll("[data-episode]");for(var z=0;z<buttons.length;z++)buttons[z].onclick=function(){var epList=eps[this.getAttribute("data-season")]||[],id=this.getAttribute("data-episode");for(var j=0;j<epList.length;j++)if(String(epList[j].id)===id){var ep=epList[j];ep.name=state.detail.item.name+" · "+(ep.title||"Capítulo "+ep.episode_num);play("series",ep);break;}};}
  function closeDetail(){state.detail=null;state.seriesInfo=null;render();focusFirst();}
  function findHistory(t,i){var key=itemKey(t,i);for(var j=0;j<state.history.length;j++)if(state.history[j].key===key)return state.history[j];return null;}
  function rememberPosition(force){if(!state.playing||state.playing.type==="live")return;var t=state.playing.type,it=state.playing.item,pos=getCurrentTime();if(!isFinite(pos)||pos<0)return;var key=itemKey(t,it);state.history=state.history.filter(function(x){return x.key!==key;});state.history.unshift({key:key,position:Math.floor(pos),time:Date.now(),name:it.name||it.title||"",media:t==="series"?{id:it.id,name:it.name||it.title||"",container_extension:it.container_extension||"mp4"}:null});state.history=state.history.slice(0,40);store.write("randy_tizen_history",state.history);}
  function play(type,item){
    if(!state.credentials)return;
    clearTimeout(zapSwitchTimer);
    stopPlayback();
    var serial=++playSerial;
    state.playing={type:type,item:item};
    state.activeKey=itemKey(type,item);
    showView("player");
    $("player-title").textContent=item.name||item.title||"Reproducción";
    $("player-status").textContent="Randy TV";
    $("live-pill").className=type==="live"?"live-pill":"live-pill hidden";
    $("player-prev").className=type==="live"?"focusable":"focusable hidden";
    $("player-next").className=type==="live"?"focusable":"focusable hidden";
    $("player-fav").className=type==="series"?"focusable hidden":"focusable";
    $("player-pause").textContent="❚❚ Pausa";
    $("player-loading").className="player-loading";
    $("track-panel").className="track-panel hidden";
    trackMode="";
    updatePlayerFav();
    if(type==="live")showZapCard(item,"Conectando con la señal…");
    else hideZapCard();
    showOverlay();
    var url=mediaURL(type,item);
    if(window.webapis&&webapis.avplay){
      try{startAvplay(url,type,item,serial);return;}
      catch(e){console.warn("AVPlay initialization error:",String(e));}
    }
    if(serial===playSerial)startHtml(url,type,item,serial);
  }
  function currentPlay(serial){return serial===playSerial&&!!state.playing;}
  function playerReady(serial,type,item){
    if(!currentPlay(serial))return;
    $("player-loading").className="player-loading hidden";
    if(type==="live"){
      updateZapStatus("Señal lista");
      clearTimeout(zapHideTimer);
      zapHideTimer=setTimeout(hideZapCard,1750);
    }
    restorePosition(type,item);
  }
  function playerError(serial,message){
    if(!currentPlay(serial))return;
    $("player-loading").className="player-loading hidden";
    if(state.playing.type==="live"){
      updateZapStatus("Señal no disponible");
      clearTimeout(zapHideTimer);
      zapHideTimer=setTimeout(hideZapCard,3600);
    }
    notify(message);
  }
  function startAvplay(url,type,item,serial){
    var av=webapis.avplay;
    playerMode="av";
    av.open(url);
    av.setDisplayRect(0,0,window.innerWidth,window.innerHeight);
    // Tizen permite como mínimo 4 s de búfer: no usar valores inferiores
    // ni bloquear la UI durante prepareAsync.
    if(type==="live"){
      try{av.setBufferingParam("PLAYER_BUFFER_FOR_PLAY","PLAYER_BUFFER_SIZE_IN_SECOND",4);}catch(e){}
      try{av.setTimeoutForBuffering(6);}catch(e){}
    }
    av.setListener({
      onbufferingstart:function(){if(!currentPlay(serial))return;$("player-loading").className="player-loading";},
      onbufferingcomplete:function(){if(!currentPlay(serial))return;playerReady(serial,type,item);},
      onstreamcompleted:function(){if(currentPlay(serial)&&type!=="live")rememberPosition(true);},
      oncurrentplaytime:function(){},
      onerror:function(error){playerError(serial,"No se puede reproducir esta señal. "+String(error));},
      onevent:function(){},
      ondrmevent:function(){},
      onsubtitlechange:function(){}
    });
    av.prepareAsync(function(){
      if(!currentPlay(serial))return;
      try{
        av.play();
        try{av.setSilentSubtitle(subtitleOff);}catch(e){}
        playerReady(serial,type,item);
      }catch(e){playerError(serial,"Error al iniciar reproducción.");}
    },function(error){
      playerError(serial,"Formato o señal no compatible: "+String(error));
    });
  }
  function startHtml(url,type,item,serial){
    if(!currentPlay(serial))return;
    playerMode="html";
    var v=$("html-player");v.style.display="block";v.src=url;
    v.onerror=function(){playerError(serial,"Formato no admitido en este televisor. Prueba una señal HLS.");};
    v.onplaying=function(){playerReady(serial,type,item);};
    v.onloadedmetadata=function(){if(currentPlay(serial))restorePosition(type,item);};
    var p=v.play();if(p&&p.catch)p.catch(function(){playerError(serial,"La señal no pudo iniciarse.");});
  }
  function restorePosition(type,item){if(type==="live")return;var h=findHistory(type,item);if(h&&h.position>30){seekTo(h.position);notify("Continuando desde donde quedaste.");}}
  function seekTo(seconds){try{if(playerMode==="av")webapis.avplay.seekTo(Math.round(seconds*1000));else if(playerMode==="html")$("html-player").currentTime=seconds;}catch(e){}}
  function getCurrentTime(){try{if(playerMode==="av")return webapis.avplay.getCurrentTime()/1000;if(playerMode==="html")return $("html-player").currentTime||0;}catch(e){}return 0;}
  function getDuration(){try{if(playerMode==="av")return webapis.avplay.getDuration()/1000;if(playerMode==="html")return $("html-player").duration||0;}catch(e){}return 0;}
  function stopPlayback(){
    // Invalidar primero todos los callbacks de prepareAsync y eventos anteriores.
    playSerial++;
    clearTimeout(zapSwitchTimer);
    clearTimeout(zapHideTimer);
    clearTimeout(overlayTimer);
    clearTimeout(digitTimer);
    if(!state.playing)return;
    rememberPosition();
    try{
      if(playerMode==="av"){
        var a=webapis.avplay;
        if(a.getState()!=="NONE")a.stop();
        a.close();
      }
    }catch(e){try{webapis.avplay.close();}catch(ignored){}}
    try{
      var v=$("html-player");v.pause();v.removeAttribute("src");v.load();v.style.display="none";
      v.onplaying=null;v.onerror=null;v.onloadedmetadata=null;
    }catch(e){}
    playerMode="";
    state.playing=null;
    clearInterval(progressTimer);progressTimer=null;
    $("track-panel").className="track-panel hidden";
    trackMode="";
    hideZapCard();
  }
  function stopPlayerIfOpen(){if(state.playing)stopPlayback();}
  function leavePlayer(){if(!state.playing)return;stopPlayback();showView("main");if(state.detail)renderDetail();else render();focusFirst();}
  function togglePause(){if(!state.playing)return;try{if(playerMode==="av"){var a=webapis.avplay,paused=a.getState()==="PAUSED";if(paused)a.play();else a.pause();$("player-pause").textContent=paused?"❚❚ Pausa":"▶ Continuar";}else{var v=$("html-player");if(v.paused){v.play();$("player-pause").textContent="❚❚ Pausa";}else{v.pause();$("player-pause").textContent="▶ Continuar";}}}catch(e){notify("No se puede pausar esta señal.");}showOverlay();}
  function showZapCard(item,status){
    var card=$("zap-card");if(!item||!card)return;
    clearTimeout(zapHideTimer);
    card.className="zap-card";
    $("zap-title").textContent=item.name||"Canal";
    var num=item.num||state.playlistIndex+1;
    $("zap-number").textContent=String(num);
    $("zap-position").textContent=state.playlist.length?(state.playlistIndex+1)+" / "+state.playlist.length:"";
    $("zap-status").textContent=status||"Conectando…";
    var logo=$("zap-logo");logo.textContent="▣";
    var src=picture(item,"live");
    if(/^https?:\/\//i.test(src)){
      var img=document.createElement("img");img.alt="";
      img.onerror=function(){logo.textContent="▣";};
      img.src=src;logo.textContent="";logo.appendChild(img);
    }
  }
  function updateZapStatus(text){
    var card=$("zap-card");
    if(card&&card.className.indexOf("hidden")===-1)$("zap-status").textContent=text;
  }
  function hideZapCard(){
    clearTimeout(zapHideTimer);
    $("zap-card").className="zap-card hidden";
  }
  function zap(step){
    if(!state.playing||state.playing.type!=="live"||!state.playlist.length)return;
    var n=state.playlist.length;
    state.playlistIndex=(state.playlistIndex+step+n)%n;
    var next=state.playlist[state.playlistIndex];
    // La ficha responde en este mismo evento de control, sin esperar a la red.
    showZapCard(next,"Cambiando de canal…");
    // Agrupar pulsaciones rápidas: evita iniciar varias decodificaciones seguidas.
    clearTimeout(zapSwitchTimer);
    zapSwitchTimer=setTimeout(function(){
      if(!state.playing||state.playing.type!=="live")return;
      play("live",next);
    },90);
  }
  function updatePlayerFav(){if(!state.playing)return;var p=state.playing;$("player-fav").textContent=isFav(p.type,p.item)?"★ Favorito":"☆ Favorito";}
  function showOverlay(){
    if(!state.playing)return;
    $("player-top").style.display="flex";
    $("player-bottom").style.display="block";
    clearTimeout(overlayTimer);
    overlayTimer=setTimeout(function(){
      if($("track-panel").className.indexOf("hidden")===-1)return;
      $("player-top").style.display="none";
      $("player-bottom").style.display="none";
    },6500);
    if(progressTimer)return;
    progressTimer=setInterval(function(){
      if(!state.playing)return;
      var dur=getDuration(),cur=getCurrentTime();
      $("progress-fill").style.width=dur>0&&isFinite(dur)?Math.max(0,Math.min(100,cur/dur*100))+"%":"0%";
      $("time-info").textContent=dur>0&&isFinite(dur)?fmt(cur)+" / "+fmt(dur):"";
    },1000);
  }
  function trackExtra(track){
    if(!track||!track.extra_info)return {};
    if(typeof track.extra_info==="object")return track.extra_info;
    try{return JSON.parse(track.extra_info);}catch(e){return {};}
  }
  function epgUrl(item){
    var c=credentials();
    return c.server+"/player_api.php?username="+encode(c.username)+"&password="+encode(c.password)+"&action=get_short_epg&stream_id="+encode(item.stream_id)+"&limit=5";
  }
  function epgText(value){
    var str=String(value===null||value===undefined?"":value);
    if(!str)return "";
    if(str.length>8192)return str.slice(0,8192);
    if(!/^[A-Za-z0-9+\/]+={0,2}$/.test(str)||str.length%4!==0)return str;
    try{
      var decoded=atob(str),encoded="";
      for(var i=0;i<decoded.length;i++)encoded+="%"+("0"+decoded.charCodeAt(i).toString(16)).slice(-2);
      var result=decodeURIComponent(encoded);
      if(/[\x00-\x08\x0b-\x1f]/.test(result))return str;
      return result;
    }catch(e){return str;}
  }
  function epgTime(item,field){
    var value=Number(item[field+"_timestamp"]);
    if(value>1000000000)return new Date(value*1000);
    var text=String(item[field]||"").replace(" ","T");
    var date=new Date(text);
    return isNaN(date.getTime())?null:date;
  }
  function epgClock(date){
    if(!date)return "—";
    return two(date.getHours())+":"+two(date.getMinutes());
  }
  function loadEpg(item,cb){
    var key=String(item&&item.stream_id||"");
    if(!key||!state.credentials){cb([]);return;}
    var cached=epgCache[key],now=Date.now();
    if(cached&&now-cached.at<120000){cb(cached.list);return;}
    if(epgPending[key]){epgPending[key].push(cb);return;}
    epgPending[key]=[cb];
    request(epgUrl(item),function(err,result){
      if(!epgPending[key])return;
      var subscribers=epgPending[key];delete epgPending[key];
      var list=!err&&result&&result.epg_listings instanceof Array?result.epg_listings:[];
      list=list.filter(function(e){return e&&typeof e==="object";}).slice(0,8);
      epgCache[key]={list:list,at:Date.now()};
      epgCacheOrder.push(key);
      if(epgCacheOrder.length>120){var remove=epgCacheOrder.shift();delete epgCache[remove];}
      for(var i=0;i<subscribers.length;i++)subscribers[i](list);
    });
  }
  function guideCurrentNext(entries){
    var now=Date.now(),current=null,next=null,ordered=entries.slice();
    ordered.sort(function(a,b){
      var aStart=epgTime(a,"start"),bStart=epgTime(b,"start");
      return (aStart?aStart.getTime():0)-(bStart?bStart.getTime():0);
    });
    for(var i=0;i<ordered.length;i++){
      var entry=ordered[i],start=epgTime(entry,"start"),end=epgTime(entry,"stop");
      if(!end)end=epgTime(entry,"end");
      if(entry.now_playing===1||entry.now_playing==="1"||(start&&end&&start.getTime()<=now&&end.getTime()>now)){
        current=entry;
        if(i+1<ordered.length)next=ordered[i+1];
        break;
      }
    }
    if(!current&&ordered.length){
      current=ordered[0];
      if(ordered.length>1)next=ordered[1];
    }
    return {current:current,next:next};
  }
  function epgProgramCard(item,kind){
    if(!item)return '<div class="guide-program '+kind+'"><span class="guide-program-status">'+(kind==="now"?"EN VIVO":"A CONTINUACIÓN")+'</span><h3>Sin información</h3><div class="guide-description">Esta señal no tiene datos de programación disponibles.</div></div>';
    var title=epgText(item.title)||"Programa sin título",desc=epgText(item.description)||"";
    var start=epgTime(item,"start"),end=epgTime(item,"stop");if(!end)end=epgTime(item,"end");
    var now=Date.now(),progress=0;
    if(start&&end&&end.getTime()>start.getTime()){
      progress=Math.max(0,Math.min(100,(now-start.getTime())/(end.getTime()-start.getTime())*100));
    }
    return '<div class="guide-program '+kind+(guideColumn===(kind==="now"?0:1)?" selected":"")+'">'+
      '<span class="guide-program-status">'+(kind==="now"?"● EN VIVO":"SIGUIENTE")+'</span>'+
      '<h3>'+safe(title)+'</h3><div class="guide-time">'+epgClock(start)+' – '+epgClock(end)+'</div>'+
      '<div class="guide-description">'+safe(desc.slice(0,160))+'</div>'+
      (kind==="now"?'<div class="guide-progress"><div class="guide-progress-fill" style="width:'+Math.round(progress)+'%"></div></div>':"")+
      '</div>';
  }
  function renderGuidePrograms(entries,channel){
    if(!guideOpen||!channel)return;
    var area=$("guide-programs"),guide=$("guide-channel-label");
    guide.textContent=channel.name||"";
    if(!entries||!entries.length){
      area.innerHTML='<div class="guide-no-epg">No hay programación disponible para este canal. Puedes verlo en vivo presionando OK.</div>';
      return;
    }
    var parsed=guideCurrentNext(entries);
    area.innerHTML=epgProgramCard(parsed.current,"now")+epgProgramCard(parsed.next,"next");
  }
  function renderGuide(){
    if(!guideOpen||!state.playlist.length)return;
    var total=state.playlist.length;
    guideIndex=(guideIndex+total)%total;
    $("guide-channel-count").textContent=(guideIndex+1)+" / "+total;
    var from=Math.max(0,Math.min(total-7,guideIndex-3));
    if(from<0)from=0;
    var to=Math.min(total,from+7),html="";
    for(var i=from;i<to;i++){
      var it=state.playlist[i],pictureUrl=picture(it,"live"),isCurrent=i===guideIndex;
      html+='<button class="guide-row focusable'+(isCurrent?' selected':'')+'" type="button" data-guide-index="'+i+'">'+
        '<span class="guide-ch-number">'+safe(it.num||i+1)+'</span>'+
        '<span class="guide-channel-icon">'+(/^https?:\/\//i.test(pictureUrl)?'<img src="'+safe(pictureUrl)+'" alt=""/>':'▣')+'</span>'+
        '<span class="guide-channel-text"><strong>'+safe(it.name)+'</strong><small>'+((state.playing&&String(state.playing.item.stream_id)===String(it.stream_id))?'● EN VIVO':'OK para ver')+'</small></span></button>';
    }
    $("guide-rail").innerHTML=html;
    var btns=$("guide-rail").querySelectorAll("[data-guide-index]");
    for(var j=0;j<btns.length;j++){
      btns[j].onclick=function(){
        guideIndex=Number(this.getAttribute("data-guide-index"));
        tuneGuideChannel();
      };
      var imgs=btns[j].querySelectorAll("img");
      for(var z=0;z<imgs.length;z++)imgs[z].onerror=function(){this.style.display="none";};
    }
    var selected=$("guide-rail").querySelector(".guide-row.selected");
    if(selected)selected.focus();
    var item=state.playlist[guideIndex],ticket=++guideToken;
    $("guide-channel-label").textContent=item.name||"";
    $("guide-programs").innerHTML='<div class="guide-no-epg">Consultando la programación…</div>';
    $("guide-favorites").textContent=isFav("live",item)?"★ Quitar favorito":"☆ Añadir favorito";
    loadEpg(item,function(list){
      if(!guideOpen||ticket!==guideToken||!state.playlist[guideIndex]||String(state.playlist[guideIndex].stream_id)!==String(item.stream_id))return;
      renderGuidePrograms(list,item);
    });
  }
  function showGuide(){
    if(!state.playing||state.playing.type!=="live"||!state.playlist.length)return;
    clearTimeout(overlayTimer);
    hideZapCard();
    guideOpen=true;guideIndex=state.playlistIndex;guideColumn=0;
    $("live-guide").className="live-guide";
    $("player-top").style.display="none";
    $("player-bottom").style.display="none";
    renderGuide();
  }
  function closeGuide(quiet){
    if(!guideOpen)return;
    guideOpen=false;guideToken++;
    $("live-guide").className="live-guide hidden";
    if(!quiet&&state.playing){
      showOverlay();
      $("player-guide").focus();
    }
  }
  function tuneGuideChannel(){
    if(!guideOpen||!state.playlist[guideIndex])return;
    var chosen=state.playlist[guideIndex],already=state.playing&&String(state.playing.item.stream_id)===String(chosen.stream_id);
    state.playlistIndex=guideIndex;
    closeGuide(true);
    if(already){showOverlay();return;}
    showZapCard(chosen,"Cambiando de canal…");
    play("live",chosen);
  }
  function guideMove(step){
    if(!guideOpen)return;
    guideIndex+=step;renderGuide();
  }
  function guideSwitchColumn(step){
    if(!guideOpen)return;
    guideColumn=Math.max(0,Math.min(1,guideColumn+step));
    var current=epgCache[String(state.playlist[guideIndex].stream_id)];
    if(current)renderGuidePrograms(current.list,state.playlist[guideIndex]);
  }
  function prettyLanguage(code){
    var lang=String(code||"").toLowerCase().replace(/_/g,"-").split("-")[0];
    var names={es:"Español",spa:"Español",en:"Inglés",eng:"Inglés",fr:"Francés",fra:"Francés",fre:"Francés",pt:"Portugués",por:"Portugués",de:"Alemán",deu:"Alemán",ger:"Alemán",it:"Italiano",ita:"Italiano",ja:"Japonés",jpn:"Japonés",ko:"Coreano",kor:"Coreano",zh:"Chino",zho:"Chino",chi:"Chino",ru:"Ruso",rus:"Ruso",und:"Idioma no especificado",unknown:"Idioma no especificado"};
    return names[lang]||String(code||"Idioma no especificado").toUpperCase();
  }
  function getTracks(mode){
    var kind=mode==="audio"?"AUDIO":"TEXT",available=[],currentIndex=-1;
    if(playerMode==="av"&&window.webapis&&webapis.avplay){
      try{
        var av=webapis.avplay;
        var all=av.getTotalTrackInfo()||[];
        for(var i=0;i<all.length;i++){
          var t=all[i];if(t.type!==kind)continue;
          var info=trackExtra(t),lang=mode==="audio"?(info.language||info.track_lang):(info.track_lang||info.language);
          var label=prettyLanguage(lang);
          available.push({index:Number(t.index),label:label});
        }
        var playing=av.getCurrentStreamInfo()||[];
        for(var j=0;j<playing.length;j++)if(playing[j].type===kind)currentIndex=Number(playing[j].index);
      }catch(e){console.warn("Tracks unavailable:",String(e));}
    }else if(playerMode==="html"){
      var v=$("html-player"),list=mode==="audio"?v.audioTracks:v.textTracks;
      if(list){
        for(var x=0;x<list.length;x++){
          var track=list[x];available.push({index:x,label:prettyLanguage(track.language||"")});
          if((mode==="audio"&&track.enabled)||(mode==="subtitles"&&track.mode==="showing"))currentIndex=x;
        }
      }
    }
    return {list:available,current:currentIndex};
  }
  function chooseTrack(mode,index){
    var info=getTracks(mode),found=false;
    for(var i=0;i<info.list.length;i++)if(info.list[i].index===index)found=true;
    if(index!==-1&&!found){notify("Esta pista ya no está disponible.");return false;}
    try{
      if(playerMode==="av"){
        if(mode==="audio"){
          if(index===-1)return false;
          webapis.avplay.setSelectTrack("AUDIO",index);
        }else{
          if(index===-1)webapis.avplay.setSilentSubtitle(true);
          else{
            webapis.avplay.setSilentSubtitle(false);
            webapis.avplay.setSelectTrack("TEXT",index);
          }
        }
      }else if(playerMode==="html"){
        var v=$("html-player"),list=mode==="audio"?v.audioTracks:v.textTracks;
        if(!list){notify("El reproductor no ofrece cambio de pistas.");return false;}
        if(mode==="audio"){
          if(index<0)return false;
          for(var x=0;x<list.length;x++)list[x].enabled=(x===index);
        }else{
          for(var y=0;y<list.length;y++)list[y].mode=(index===y?"showing":"disabled");
        }
      }else return false;
      if(mode==="subtitles"){
        subtitleOff=index===-1;
        store.write("randy_tizen_subtitles_off",subtitleOff);
      }
      notify(mode==="audio"?"Pista de audio seleccionada.":index===-1?"Subtítulos desactivados.":"Subtítulos activados.");
      return true;
    }catch(e){
      notify("La señal no permite cambiar esta pista.");
      return false;
    }
  }
  function openTrackPanel(mode){
    if(!state.playing)return;
    clearTimeout(overlayTimer);
    trackMode=mode;
    $("track-title").textContent=mode==="audio"?"Pistas de audio":"Subtítulos";
    $("track-description").textContent=mode==="audio"?"Selecciona el idioma de audio que tenga esta señal.":"Activa, desactiva o cambia los subtítulos disponibles.";
    $("track-panel").className="track-panel";
    showOverlay();
    renderTrackOptions();
  }
  function renderTrackOptions(){
    var panel=$("track-panel"),el=$("track-options");if(!trackMode)return;
    el.innerHTML="";
    var info=getTracks(trackMode),items=info.list;
    function option(text,index,selected){
      var b=document.createElement("button");b.type="button";
      b.className="track-option focusable"+(selected?" selected":"");
      b.setAttribute("aria-pressed",selected?"true":"false");
      var label=document.createElement("span");label.textContent=text;b.appendChild(label);
      if(selected){var check=document.createElement("small");check.textContent="✓ ACTUAL";b.appendChild(check);}
      b.onclick=function(){if(chooseTrack(trackMode,index))closeTrackPanel();else renderTrackOptions();};
      el.appendChild(b);
    }
    if(trackMode==="subtitles")option("Desactivados",-1,subtitleOff);
    for(var i=0;i<items.length;i++){
      var track=items[i];
      option(track.label+" · Pista "+(i+1),track.index,trackMode==="subtitles"?!subtitleOff&&(info.current===track.index||(info.current<0&&i===0)):info.current===track.index||(info.current<0&&i===0));
    }
    if(!items.length){
      var msg=document.createElement("div");msg.className="track-empty";
      msg.textContent=trackMode==="audio"?"No hay pistas alternativas disponibles en esta señal.":"La señal no informa pistas de subtítulos. Puedes dejarlos desactivados.";
      el.appendChild(msg);
    }
    var first=el.querySelector(".track-option.selected")||el.querySelector(".track-option")||$("track-close");
    if(first)first.focus();
  }
  function closeTrackPanel(){
    $("track-panel").className="track-panel hidden";
    var returnTo=trackMode==="audio"?$("player-audio"):$("player-subtitles");
    trackMode="";
    showOverlay();
    if(returnTo)returnTo.focus();
  }
  function moveTrackFocus(step){
    var nodes=$("track-panel").querySelectorAll(".focusable"),visible=[];
    for(var i=0;i<nodes.length;i++)if(nodes[i].getBoundingClientRect().height>0)visible.push(nodes[i]);
    if(!visible.length)return;
    var idx=visible.indexOf(document.activeElement),next=(idx+step+visible.length)%visible.length;
    visible[next].focus();
    if(visible[next].scrollIntoView)visible[next].scrollIntoView({block:"nearest"});
  }
  function two(n){return n<10?"0"+n:String(n);}function fmt(s){var n=Math.floor(s)||0;return two(Math.floor(n/3600))+":"+two(Math.floor(n%3600/60))+":"+two(n%60);}
  function clearSettings(){stopPlayback();state.credentials=null;state.live=[];state.vod=[];state.series=[];state.favorites=store.read("randy_tizen_favorites",[]);store.remove("randy_tizen_login");$("password").value="";showView("setup");setLoginError("");$("server").focus();}
  function settings(){if(confirm("¿Quieres cambiar los datos de acceso?"))clearSettings();}
  function moveFocus(key){var nodes=document.querySelectorAll(".view:not(.hidden) .focusable");var valid=[];for(var i=0;i<nodes.length;i++){var n=nodes[i],r=n.getBoundingClientRect();if(r.width>0&&r.height>0&&getComputedStyle(n).visibility!=="hidden")valid.push(n);}if(!valid.length)return;var current=document.activeElement,index=valid.indexOf(current);if(index<0){valid[0].focus();return;}var r0=current.getBoundingClientRect(),x0=(r0.left+r0.right)/2,y0=(r0.top+r0.bottom)/2,nearest=null,best=Infinity;for(var j=0;j<valid.length;j++){var e=valid[j];if(e===current)continue;var r=e.getBoundingClientRect(),x=(r.left+r.right)/2,y=(r.top+r.bottom)/2,dx=x-x0,dy=y-y0;if(key==="left"&&dx>=-5||key==="right"&&dx<=5||key==="up"&&dy>=-5||key==="down"&&dy<=5)continue;var primary=key==="left"||key==="right"?Math.abs(dx):Math.abs(dy),secondary=key==="left"||key==="right"?Math.abs(dy):Math.abs(dx),score=primary+secondary*2.3;if(score<best){best=score;nearest=e;}}if(nearest){nearest.focus();if(nearest.scrollIntoView)nearest.scrollIntoView({block:"nearest",inline:"nearest"});}}
  function pressBack(){if($("track-panel").className.indexOf("hidden")===-1){closeTrackPanel();return;}if(state.playing){leavePlayer();return;}if(state.detail){closeDetail();return;}if(state.section!=="home"){selectSection("home");return;}notify("Ya estás en Inicio. Usa las flechas para elegir.");}
  function numberKey(num){if(!state.playing||state.playing.type!=="live")return;state.remoteDigits=(state.remoteDigits+num).slice(-4);$("number-bubble").textContent=state.remoteDigits;$("number-bubble").className="number-bubble";clearTimeout(digitTimer);digitTimer=setTimeout(function(){var n=parseInt(state.remoteDigits,10);state.remoteDigits="";$("number-bubble").className="number-bubble hidden";if(!n)return;var match=null;for(var j=0;j<state.playlist.length;j++){if(Number(state.playlist[j].num)===n){match=j;break;}}if(match===null&&n<=state.playlist.length)match=n-1;if(match!==null){state.playlistIndex=match;showZapCard(state.playlist[match],"Cambiando de canal…");play("live",state.playlist[match]);}else notify("No existe el canal "+n+" en esta lista.");},900);}
  function keyDown(event){var k=event.keyCode||event.which,playing=!!state.playing,typing=document.activeElement&&(/INPUT|TEXTAREA/.test(document.activeElement.tagName));var defs={37:"left",38:"up",39:"right",40:"down"};if(k===10009||k===8||k===27){if(typing){document.activeElement.blur();return;}event.preventDefault();pressBack();return;}
    if(playing){if($("track-panel").className.indexOf("hidden")===-1){if(k===38||k===40){event.preventDefault();moveTrackFocus(k===40?1:-1);return;}if(k===13){event.preventDefault();var chosen=document.activeElement;if(chosen&&chosen.tagName==="BUTTON")chosen.click();return;}if(k===37||k===39){event.preventDefault();moveTrackFocus(k===39?1:-1);return;}return;}if(k===405){event.preventDefault();openTrackPanel("subtitles");return;}if(k===404){event.preventDefault();openTrackPanel("audio");return;}if(k>=48&&k<=57){event.preventDefault();numberKey(String(k-48));return;}if(k===427||k===33||k===38){event.preventDefault();zap(1);return;}if(k===428||k===34||k===40){event.preventDefault();zap(-1);return;}if(k===415||k===19||k===10252){event.preventDefault();togglePause();return;}if(k===413){event.preventDefault();leavePlayer();return;}if(k===37||k===39){event.preventDefault();if(state.playing.type!=="live")seekTo(Math.max(0,getCurrentTime()+(k===39?15:-15)));showOverlay();return;}if(k===13){event.preventDefault();if($("player-bottom").style.display==="none"){showOverlay();$("player-pause").focus();}else if(document.activeElement&&document.activeElement.tagName==="BUTTON")document.activeElement.click();else showOverlay();return;}showOverlay();return;}
    if(typing){if(k===13&&document.activeElement.type==="checkbox"){event.preventDefault();document.activeElement.click();return;}if(k===38||k===40){event.preventDefault();moveFocus(k===38?"up":"down");return;}if(k===13){event.preventDefault();document.activeElement.blur();}return;}
    if(defs[k]){event.preventDefault();moveFocus(defs[k]);return;}if(k===13){var a=document.activeElement;if(a&&a.tagName==="BUTTON"){event.preventDefault();a.click();}return;}if(k===427||k===428){event.preventDefault();selectSection("live");return;}
  }
  function registerKeys(){try{if(window.tizen&&tizen.tvinputdevice){tizen.tvinputdevice.registerKeyBatch(["ChannelUp","ChannelDown","MediaPlayPause","MediaPlay","MediaPause","MediaStop","ColorF0Red","ColorF1Green","ColorF2Yellow","ColorF3Blue"]);}}catch(e){console.warn("Remote key registration:",e);}}
  function updateClock(){
    var now=new Date(),el=$("clock-display");
    if(el)el.textContent=two(now.getHours())+":"+two(now.getMinutes());
  }
  function attach(){document.addEventListener("keydown",keyDown);$("login-form").addEventListener("submit",startLogin);$("go-home").onclick=function(){selectSection("home");};$("settings-button").onclick=settings;
    var tabs=document.querySelectorAll("[data-nav]");for(var i=0;i<tabs.length;i++)tabs[i].onclick=function(){selectSection(this.getAttribute("data-nav"));};
    $("player-back").onclick=leavePlayer;$("player-prev").onclick=function(){zap(-1);};$("player-next").onclick=function(){zap(1);};$("player-pause").onclick=togglePause;$("player-fav").onclick=function(){if(state.playing)toggleFav(state.playing.type,state.playing.item);};$("player-audio").onclick=function(){openTrackPanel("audio");};$("player-subtitles").onclick=function(){openTrackPanel("subtitles");};$("track-close").onclick=closeTrackPanel;
    document.addEventListener("visibilitychange",function(){if(document.hidden&&state.playing){rememberPosition();try{if(playerMode==="av")webapis.avplay.pause();else $("html-player").pause();}catch(e){}}});
    window.addEventListener("beforeunload",function(){stopPlayback();});
    registerKeys();updateClock();setInterval(updateClock,60000);var local=window.RANDY_LOCAL_CONFIG;var saved=(local&&local.server&&local.username&&local.password)?local:store.read("randy_tizen_login",null);if(saved&&saved.server&&saved.username&&saved.password){state.credentials=saved;$("server").value=saved.server;$("username").value=saved.username;$("password").value=saved.password;launch();}else{showView("setup");$("server").focus();}
  }
  if(document.readyState==="loading")document.addEventListener("DOMContentLoaded",attach);else attach();
})();
