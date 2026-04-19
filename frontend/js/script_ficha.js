//DOM
const contFicha = document.querySelector(".bloque-pestana");
const colIzq = document.querySelector(".col-izq");
const colDcha = document.querySelector(".col-dcha");
const btn_modificar = document.querySelector(".modificar");
const btn_guardar = document.querySelector(".boton-accion-guardar");
const btn_fitxes = document.querySelector(".enlace-volver");
const btnPDF = document.querySelector('.boton-accion-exportar');

let modoEdicion = false;

//inicializar mapa 
const initMapa = function(latitud, altitud) {
    let map = L.map('map').setView([latitud, altitud], 8); //setView([latitud,longitud],zoom)

    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
    }).addTo(map);
    
}

const crearDiv = function(classCSS){
    const div = document.createElement('div');
    div.classList.add(classCSS);
    return div;
}

const crearCampo = function(classCSS, labelText, value, inputId) {
    const div = document.createElement('div');
    div.classList.add(classCSS);
    
    const label = document.createElement('label');
    label.textContent = labelText;
    
    const input = document.createElement('input');
    input.type = 'text';
    input.value = value;
    input.disabled = true; //lo bloqueamos de primeras
    if (inputId) input.setAttribute('id', inputId); //id para introducir datos despues
    
    div.appendChild(label);
    div.appendChild(input);
    return div;
};

const params = new URLSearchParams(window.location.search);
const tab = params.get('tab');
const id = params.get('id');
const cargarFicha = async function() {
    try {
        let coleccio;
        if (tab === 'sector') coleccio = 'sectors';
        else if (tab === 'yacimiento') coleccio = 'jaciments';
        else if (tab === 'ue') coleccio = 'unitats_estratigrafiques';

        const doc = await db.collection(coleccio).doc(id).get();
        
        if (!doc.exists) {
            console.error("Document no trobat");
            return;
        }
        
        const dades = doc.data();
        console.log("Dades:", dades); // para verificar qué llega
        
        if (tab === 'sector') {
            //COLUMNA IZQUIERDA
            //nombre y yacimiento
            const divNom = crearDiv('grupo-rejilla-interno');
            divNom.appendChild(crearCampo("campo-formulario-base","Nom",dades.nom, "i-sec-nom"));
            divNom.appendChild(crearCampo("campo-formulario-base","Codi",dades.codi_sector, "i-sec-codi"));
            divNom.appendChild(crearCampo("campo-formulario-base","Jaciment",dades.codi_jaciment,"i-sec-codiJac")); 
            colIzq.appendChild(divNom);

            //descripcion
            const divDescrip = crearDiv('campo-area-texto');
            const labelDescrip = document.createElement('label');
            labelDescrip.textContent = "Descripció";
            const inputDescrip = document.createElement('textarea');
            inputDescrip.textContent = dades.descripcio;
            inputDescrip.disabled = true;
            inputDescrip.id="i-sec-descr";
            divDescrip.appendChild(labelDescrip);
            divDescrip.appendChild(inputDescrip);
            colIzq.appendChild(divDescrip);

            //COLUMNA DERECHA
            //imágenes
            const titulo = crearDiv('titulo-bloque-interno');
            titulo.textContent="Imatges";
            colDcha.appendChild(titulo);
            const contImg = crearDiv('caja-arrastrar-soltar');
            contImg.id="i-sec-img";
            colDcha.appendChild(contImg);

            if (dades.imatges_urls && dades.imatges_urls.length > 0) {
                dades.imatges_urls.forEach(url => {
                    const img = document.createElement('img');
                    img.src = url;
                    img.style.width = '100px';
                    img.style.height = '100px';
                    img.style.objectFit = 'cover';
                    img.style.borderRadius = '8px';
                    contImg.appendChild(img);
                });
            }else{
                contImg.textContent = "No hi ha cap imatge";
            }

        }else if (tab === 'yacimiento'){
            //COLUMNA IZQUIERDA
            //nombre y directora
            const divNom = crearDiv('grupo-rejilla-interno');
            divNom.appendChild(crearCampo("campo-formulario-base","Nom",dades.nom,"i-jac-nom"));
            divNom.appendChild(crearCampo("campo-formulario-base","Codi",dades.codi_jaciment,"i-jac-codi"));
            divNom.appendChild(crearCampo("campo-formulario-base","Director/a",dades.director,"i-jac-director"));
            colIzq.appendChild(divNom);
            //coordenadas
            const divCoor = crearDiv('campo-formulario-base');

            const labelCoord = document.createElement('label');
            labelCoord.textContent = "Coordenades";
            const labelAltitud = document.createElement('label');
            labelAltitud.textContent = "Altitud x Latitud x Profunditat";
            const divMedidas = crearDiv('campo-doble-medida');

            const altitud = document.createElement('input');
            altitud.type = 'number';
            altitud.value = dades.coordenada_x || '';
            altitud.disabled = true;
            altitud.id = "i-jac-alt";
            const latitud = document.createElement('input');
            latitud.type = 'number';
            latitud.value = dades.coordenada_y || '';
            latitud.disabled = true;
            latitud.id = "i-jac-lat";
            const profundidad = document.createElement('input');
            profundidad.type = 'number';
            profundidad.value = dades.coordenada_z || '';
            profundidad.disabled = true;
            profundidad.id = "i-jac-prof";
            divMedidas.appendChild(altitud);
            divMedidas.appendChild(latitud);
            divMedidas.appendChild(profundidad);
            divCoor.appendChild(labelCoord);
            divCoor.appendChild(labelAltitud);
            divCoor.appendChild(divMedidas);
            colIzq.appendChild(divCoor);

            //descripcion
            const divDescrip = crearDiv('campo-area-texto');
            const labelDescrip = document.createElement('label');
            labelDescrip.textContent = "Descripció";
            const inputDescrip = document.createElement('textarea');
            inputDescrip.textContent = dades.descripcio || 'No hi ha cap descripciò';
            inputDescrip.disabled = true;
            inputDescrip.id = "i-jac-descr";
            divDescrip.appendChild(labelDescrip);
            divDescrip.appendChild(inputDescrip);
            colIzq.appendChild(divDescrip);

            //COLUMNA DERECHA
            //imágenes
            const titIMG = crearDiv('titulo-bloque-interno');
            titIMG.textContent="Imatges";
            colDcha.appendChild(titIMG);
            const contImg = crearDiv('caja-arrastrar-soltar');
            contImg.id = "i-jac-img";
            colDcha.appendChild(contImg);
            if (dades.imatges_urls && dades.imatges_urls.length > 0) {
                dades.imatges_urls.forEach(url => {
                    const img = document.createElement('img');
                    img.src = url;
                    img.style.width = '100px';
                    img.style.height = '100px';
                    img.style.objectFit = 'cover';
                    img.style.borderRadius = '8px';
                    contImg.appendChild(img);
                });
            }else{
                contImg.textContent = "No hi ha cap imatge";
            }
            //mapa
            const titMAP = crearDiv('titulo-bloque-interno');
            titMAP.textContent="Map";
            colDcha.appendChild(titMAP);
            const contMapa = crearDiv('container-map');
            contMapa.id = 'map';
            colDcha.appendChild(contMapa);
            initMapa(latitud.value, altitud.value);

        }else if(tab === 'ue'){
            //COLUMNA IZQUIERDA
            //info basica
            const divInfo = crearDiv('grupo-rejilla-interno');
            divInfo.appendChild(crearCampo("campo-formulario-base","UE",dades.codi_ue,"i-ue-codi")); //ok
            divInfo.appendChild(crearCampo("campo-formulario-base","Sector",dades.codi_sector,"i-ue-codiSec")); //ok
            divInfo.appendChild(crearCampo("campo-formulario-base","Jaciment",dades.codi_jaciment,"i-ue-codiJac"));
            divInfo.appendChild(crearCampo("campo-formulario-base","Codi intervencio",dades.codi_intervencio,"i-ue-codiInterv"));
            divInfo.appendChild(crearCampo("campo-formulario-base","Datacio",dades.cronologia,"i-ue-cronolog")); //ok
            //divInfo.appendChild(crearCampo("campo-formulario-base","Registrat per",dades.registrat_per,"i-ue-person")); //ok
            divInfo.appendChild(crearCampo("campo-formulario-base","Estat conservacio",dades.estat_conservacio,"i-ue-estado")); //ok
            divInfo.appendChild(crearCampo("campo-formulario-base","Tipus UE",dades.tipus_ue,"i-ue-tipus")); //ok
            divInfo.appendChild(crearCampo("campo-formulario-base","Textura",dades.textura,"i-ue-text")); //ok
            divInfo.appendChild(crearCampo("campo-formulario-base","Color",dades.color,"i-ue-color")); //ok
            divInfo.appendChild(crearCampo("campo-formulario-base","Material",dades.material,"i-ue-mat")); //ok
            divInfo.appendChild(crearCampo("campo-formulario-base","Interpretació",dades.interpretacio,"i-ue-interpr"));
            colIzq.appendChild(divInfo);
            //medidas topograficas
            const titTopo = crearDiv('titulo-bloque-interno');
            titTopo.textContent="Dades topográfiques";
            colIzq.appendChild(titTopo);
            const divCoor = crearDiv('campo-formulario-base');

            const labelCoord = document.createElement('label');
            labelCoord.textContent = "Dimensions (cm)";
            const labelAltitud = document.createElement('label');
            labelAltitud.textContent = "Longitud x Amplada x Alçada";
            const divMedidas = crearDiv('campo-doble-medida');

            const longitud = document.createElement('input');
            longitud.type = 'number';
            longitud.value = 25;
            longitud.disabled = true;
            longitud.id="i-ue-long";
            const amplada = document.createElement('input');
            amplada.type = 'number';
            amplada.value = 41.402765;
            amplada.disabled = true;
            amplada.id="i-ue-ampl";
            const alcada = document.createElement('input');
            alcada.type = 'number';
            alcada.value = 55;
            alcada.disabled = true;
            alcada.id="i-ue-alc";
            divMedidas.appendChild(longitud);
            divMedidas.appendChild(amplada);
            divMedidas.appendChild(alcada);
            divCoor.appendChild(labelCoord);
            divCoor.appendChild(labelAltitud);
            divCoor.appendChild(divMedidas);
            colIzq.appendChild(divCoor);

            //cotes
            const divCotes = crearDiv('grupo-rejilla-interno');
            const cotes = crearDiv('campo-formulario-base');
            const labelCotes = document.createElement('label');
            labelCotes.textContent = "Cotes (m) - Sup x Inf";
            cotes.appendChild(labelCotes);
            const inputsCotes = crearDiv('campo-doble-medida');
            
            const sup = document.createElement('input');
            sup.type = 'number';
            sup.value = 25;
            sup.disabled = true;
            sup.id="i-ue-cotaSup";
            const inf = document.createElement('input');
            inf.type = 'number';
            inf.value = 41.402765;
            inf.disabled = true;
            inf.id="i-ue-cotaInf";

            inputsCotes.appendChild(sup);
            inputsCotes.appendChild(inf);
            cotes.appendChild(labelCotes);
            cotes.appendChild(inputsCotes);
            divCotes.appendChild(cotes);
            colIzq.appendChild(divCotes);

            //COLUMNA DERECHA
            //relaciones
            //ok
            //muestro campos
            document.querySelector('.relacions').style.display = 'block';
            //relleno valores si existen
            if (dades.relacions) {
                dades.relacions.forEach(rel => {
                    const input = document.querySelector(`.rel-${rel.tipus}`);
                    if (input) input.value = rel.desti;
                });
            }

            //descripcion
            //ok
            const divDescrip = crearDiv('campo-area-texto');
            const labelDescrip = document.createElement('label');
            labelDescrip.textContent = "Descripció";
            const inputDescrip = document.createElement('textarea');
            inputDescrip.textContent = dades.descripcio || 'No hi ha cap descripciò';
            inputDescrip.disabled = true;
            inputDescrip.id="i-ue-descr";
            divDescrip.appendChild(labelDescrip);
            divDescrip.appendChild(inputDescrip);
            colDcha.appendChild(divDescrip);
            //imágenes
            const titIMG = crearDiv('titulo-bloque-interno');
            titIMG.textContent="Imatges";
            colDcha.appendChild(titIMG);
            const contImg = crearDiv('caja-arrastrar-soltar');
            contImg.id="i-ue-img";
            colDcha.appendChild(contImg);
            if (dades.imatges_urls && dades.imatges_urls.length > 0) {
                dades.imatges_urls.forEach(url => {
                    const img = document.createElement('img');
                    img.src = url;
                    img.style.width = '100px';
                    img.style.height = '100px';
                    img.style.objectFit = 'cover';
                    img.style.borderRadius = '8px';
                    contImg.appendChild(img);
                });
            }else{
                contImg.textContent = "No hi ha cap imatge";
            }

        }
    }catch (error){
        console.log("Error carregant la fitxa: ", error);
    }
};
cargarFicha();

//botón fitxes - volver
const volver = function (){ 
    if (modoEdicion){
        let respuesta = confirm("Estàs segur que vols sortir?");
        if(respuesta){
            modoEdicion = false;
            //volver a la página inicial
            window.location.assign("libreria.html");
        }
    }else{
        window.location.assign("libreria.html");
    }
    
};
//si estamos editando y da click a otro botón, sale alerta
document.querySelectorAll('.cabecera-principal a, .cabecera-principal button').forEach(function(element) {
    element.addEventListener("click", function(event) {
        if (modoEdicion) {
            event.preventDefault();
            let respuesta = confirm("Tens canvis sense desar. Estàs segur que vols sortir?");
            if (respuesta) {
                modoEdicion = false;
                window.location.assign(this.href || "libreria.html");
            }
        }
    });
});

btn_fitxes.addEventListener("click", function(){
    volver();
})

btn_modificar.addEventListener("click", function(){
    modoEdicion = true;
    //desbloquear todos los inputs
    document.querySelectorAll('.bloque-pestana input, .bloque-pestana textarea').forEach(camp => {
        camp.disabled = false;
    });
    
});

btn_guardar.addEventListener('click', async function() {
    modoEdicion = false;

    try {
        let coleccio;
        if (tab === 'sector') coleccio = 'sectors';
        else if (tab === 'yacimiento') coleccio = 'jaciments';
        else if (tab === 'ue') coleccio = 'unitats_estratigrafiques';

        // Recogemos los valores actuales de los inputs
        let dadesActualitzades = {};

        if (tab === 'sector') {
            const inputs = document.querySelectorAll('.col-izq input, .col-izq textarea');
            
            dadesActualitzades = {
                nom: document.getElementById('i-sec-nom').value,
                codi_sector: document.getElementById('i-sec-codi').value,
                codi_jaciment: document.getElementById('i-sec-codiJac').value,
                descripcio: document.getElementById('i-sec-descr').value
            };
        } else if (tab === 'yacimiento') {
            const inputs = document.querySelectorAll('.col-izq input, .col-izq textarea');
            dadesActualitzades = {
                nom: document.getElementById('i-jac-nom').value,
                codi_jaciment: document.getElementById('i-jac-codi').value,
                director: document.getElementById('i-jac-director').value,
                coordenada_x: document.getElementById('i-jac-alt').value,
                coordenada_y: document.getElementById('i-jac-lat').value,
                coordenada_z: document.getElementById('i-jac-prof').value,
                descripcio: document.getElementById('i-jac-descr').value,
            };
        } else if (tab === 'ue') {
            const inputs = document.querySelectorAll('.col-izq input, .col-izq textarea');
            dadesActualitzades = {
                codi_ue: document.getElementById('i-ue-codi').value,
                codi_sector: document.getElementById('i-ue-codiSec').value,
                codi_jaciment: document.getElementById('i-ue-codiJac').value,
                codi_intervencio: document.getElementById('i-ue-codiInterv').value,
                tipus_ue: document.getElementById('i-ue-tipus').value,
                textura: document.getElementById('i-ue-text').value,
                color: document.getElementById('i-ue-color').value,
                material: document.getElementById('i-ue-mat').value,
                cronologia: document.getElementById('i-ue-cronolog').value,
                estat_conservacio: document.getElementById('i-ue-estado').value,
                //registrat_per: document.getElementById('i-ue-person').value,
                interpretacio: document.getElementById('i-ue-interpr').value,
                descripcio: document.getElementById('i-ue-descr').value,
                longitud: document.getElementById('i-ue-long').value,
                amplada: document.getElementById('i-ue-ampl').value,
                alcada: document.getElementById('i-ue-alc').value,
                cota_sup: document.getElementById('i-ue-cotaSup').value,
                cota_inf: document.getElementById('i-ue-cotaInf').value,
                //recogemos las relaciones y construimos el array
                //pero con el filter solo guardamos las que tienen valor
                relacions: [
                    { tipus: 'igual_a', desti: document.querySelector('.rel-igual_a').value },
                    { tipus: 'cobreix', desti: document.querySelector('.rel-cobreix').value },
                    { tipus: 'cobert_per', desti: document.querySelector('.rel-cobert_per').value },
                    { tipus: 'farceix', desti: document.querySelector('.rel-farceix').value },
                    { tipus: 'farcit_per', desti: document.querySelector('.rel-farcit_per').value },
                    { tipus: 'talla', desti: document.querySelector('.rel-talla').value },
                    { tipus: 'tallat_per', desti: document.querySelector('.rel-tallat_per').value },
                    { tipus: 'recolza', desti: document.querySelector('.rel-recolza').value },
                    { tipus: 'se_li_recolza', desti: document.querySelector('.rel-se_li_recolza').value },
                    { tipus: 'lliura', desti: document.querySelector('.rel-lliura').value },
                    { tipus: 'se_li_lliura', desti: document.querySelector('.rel-se_li_lliura').value }
                ].filter(rel => rel.desti !== '')
            };
        }

        await db.collection(coleccio).doc(id).update(dadesActualitzades);
        alert("Desat correctament!");

    } catch (error) {
        console.error("Error desant:", error);
        alert("Error en desar els canvis");
    }

    // Bloqueamos inputs otra vez
    document.querySelectorAll('.bloque-pestana input, .bloque-pestana textarea').forEach(camp => {
        camp.disabled = true;
    });
});

btnPDF.addEventListener('click', function() {
    window.print();
});