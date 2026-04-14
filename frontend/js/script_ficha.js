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
const initMapa = function() {
    let map = L.map('map').setView([41.402765, 2.194551], 8); //setView([latitud,longitud],zoom)

    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
    }).addTo(map);
    
}

const crearDiv = function(classCSS){
    const div = document.createElement('div');
    div.classList.add(classCSS);
    return div;
}

const crearCampo = function(classCSS, labelText, value) {
    const div = document.createElement('div');
    div.classList.add(classCSS);
    
    const label = document.createElement('label');
    label.textContent = labelText;
    
    const input = document.createElement('input');
    input.type = 'text';
    input.value = value;
    input.disabled = true; //lo bloqueamos de primeras
    
    div.appendChild(label);
    div.appendChild(input);
    return div;
};

const params = new URLSearchParams(window.location.search);
const tab = params.get('tab');
const id = params.get('id');
const cargarFicha = async function() {
    try {
        const respuesta = await fetch(`http://127.0.0.1:8000/${tab}/${id}`);
        if (!respuesta.ok) throw new Error("Error carregant la fitxa");
        const dades = await respuesta.json();
        console.log(dades);
        if (tab === 'sector') {
            //COLUMNA IZQUIERDA
            //nombre y yacimiento
            const divNom = crearDiv('grupo-rejilla-interno');
            divNom.appendChild(crearCampo("campo-formulario-base","Nom/Codi",dades.nom));
            divNom.appendChild(crearCampo("campo-formulario-base","Jaciment",dades.id_jaciment));
            colIzq.appendChild(divNom);

            //descripcion
            const divDescrip = crearDiv('campo-area-texto');
            const labelDescrip = document.createElement('label');
            labelDescrip.textContent = "Descripció";
            const inputDescrip = document.createElement('textarea');
            inputDescrip.textContent = dades.descripcio;
            inputDescrip.disabled = true;
            divDescrip.appendChild(labelDescrip);
            divDescrip.appendChild(inputDescrip);
            colIzq.appendChild(divDescrip);

            //COLUMNA DERECHA
            //imágenes
            const titulo = crearDiv('titulo-bloque-interno');
            titulo.textContent="Imatges";
            colDcha.appendChild(titulo);
            const contImg = crearDiv('caja-arrastrar-soltar');
            colDcha.appendChild(contImg);

        }else if (tab === 'yacimiento'){
            //COLUMNA IZQUIERDA
            //nombre y directora
            const divNom = crearDiv('grupo-rejilla-interno');
            divNom.appendChild(crearCampo("campo-formulario-base","Nom/Codi","ue prueba"));
            divNom.appendChild(crearCampo("campo-formulario-base","Director/a","Paloma"));
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
            altitud.value = 25;
            altitud.disabled = true;
            const latitud = document.createElement('input');
            latitud.type = 'number';
            latitud.value = 41.402765;
            latitud.disabled = true;
            const profundidad = document.createElement('input');
            profundidad.type = 'number';
            profundidad.value = 55;
            profundidad.disabled = true;
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
            inputDescrip.textContent = 'descripcion de prueba';
            inputDescrip.disabled = true;
            divDescrip.appendChild(labelDescrip);
            divDescrip.appendChild(inputDescrip);
            colIzq.appendChild(divDescrip);

            //COLUMNA DERECHA
            //imágenes
            const titIMG = crearDiv('titulo-bloque-interno');
            titIMG.textContent="Imatges";
            colDcha.appendChild(titIMG);
            const contImg = crearDiv('caja-arrastrar-soltar');
            colDcha.appendChild(contImg);
            //mapa
            const titMAP = crearDiv('titulo-bloque-interno');
            titMAP.textContent="Map";
            colDcha.appendChild(titMAP);
            const contMapa = crearDiv('container-map');
            contMapa.id = 'map';
            colDcha.appendChild(contMapa);
            initMapa();

        }else if(tab === 'ue'){
            //COLUMNA IZQUIERDA
            //info basica
            const divInfo = crearDiv('grupo-rejilla-interno');
            divInfo.appendChild(crearCampo("campo-formulario-base","UE","ue prueba"));
            divInfo.appendChild(crearCampo("campo-formulario-base","Sector","Paloma"));
            divInfo.appendChild(crearCampo("campo-formulario-base","Jaciment","Paloma"));
            divInfo.appendChild(crearCampo("campo-formulario-base","Codi intervencio","Paloma"));
            divInfo.appendChild(crearCampo("campo-formulario-base","Datacio","Paloma"));
            divInfo.appendChild(crearCampo("campo-formulario-base","Tipus UE","Estrat"));
            divInfo.appendChild(crearCampo("campo-formulario-base","Textura","Paloma"));
            divInfo.appendChild(crearCampo("campo-formulario-base","Color","Paloma"));
            divInfo.appendChild(crearCampo("campo-formulario-base","Material","Paloma"));
            divInfo.appendChild(crearCampo("campo-formulario-base","Interpretació","Paloma"));
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
            const amplada = document.createElement('input');
            amplada.type = 'number';
            amplada.value = 41.402765;
            amplada.disabled = true;
            const alcada = document.createElement('input');
            alcada.type = 'number';
            alcada.value = 55;
            alcada.disabled = true;
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
            const inf = document.createElement('input');
            inf.type = 'number';
            inf.value = 41.402765;
            inf.disabled = true;

            inputsCotes.appendChild(sup);
            inputsCotes.appendChild(inf);
            cotes.appendChild(labelCotes);
            cotes.appendChild(inputsCotes);
            divCotes.appendChild(cotes);
            colIzq.appendChild(divCotes);

            //COLUMNA DERECHA
            //relaciones
            const titRelac = crearDiv('titulo-bloque-interno');
            titRelac.textContent="Relacions";
            colDcha.appendChild(titRelac);
            const divRel1 = crearDiv('grupo-rejilla-interno');
            divRel1.appendChild(crearCampo("campo-formulario-base","Igual a","ue prueba"));
            const divRel = crearDiv('grupo-rejilla-interno');
            divRel.appendChild(crearCampo("campo-formulario-base","Cobreix","Paloma"));
            divRel.appendChild(crearCampo("campo-formulario-base","Cobert per","Paloma"));
            divRel.appendChild(crearCampo("campo-formulario-base","Farceix","Paloma"));
            divRel.appendChild(crearCampo("campo-formulario-base","Farcit per","Paloma"));
            divRel.appendChild(crearCampo("campo-formulario-base","Talla","Estrat"));
            divRel.appendChild(crearCampo("campo-formulario-base","Tallat per","Estrat"));
            divRel.appendChild(crearCampo("campo-formulario-base","Recolza","Estrat"));
            divRel.appendChild(crearCampo("campo-formulario-base","Se li recolza","Estrat"));
            divRel.appendChild(crearCampo("campo-formulario-base","Lliura","Estrat"));
            divRel.appendChild(crearCampo("campo-formulario-base","Se li lliura","Estrat"));
            colDcha.appendChild(divRel1);
            colDcha.appendChild(divRel);
            //descripcion
            const divDescrip = crearDiv('campo-area-texto');
            const labelDescrip = document.createElement('label');
            labelDescrip.textContent = "Descripció";
            const inputDescrip = document.createElement('textarea');
            inputDescrip.textContent = 'descripcion de prueba';
            inputDescrip.disabled = true;
            divDescrip.appendChild(labelDescrip);
            divDescrip.appendChild(inputDescrip);
            colDcha.appendChild(divDescrip);
            //imágenes
            const titIMG = crearDiv('titulo-bloque-interno');
            titIMG.textContent="Imatges";
            colDcha.appendChild(titIMG);
            const contImg = crearDiv('caja-arrastrar-soltar');
            colDcha.appendChild(contImg);

        }
    }catch (error){
        console.log("Error: ", error);
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

btn_guardar.addEventListener('click', function() {
    modoEdicion = false;
    //aquí irá el fetch PUT
    //bloqueamos otra vez
    document.querySelectorAll('.bloque-pestana input, .bloque-pestana textarea').forEach(camp => {
        camp.disabled = true;
    });
    
});

btnPDF.addEventListener('click', function() {
    window.print();
});