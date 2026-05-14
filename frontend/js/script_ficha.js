//DOM
const contFicha = document.querySelector(".bloque-pestana");
const colIzq = document.querySelector(".col-izq");
const colDcha = document.querySelector(".col-dcha");
const btn_modificar = document.querySelector(".modificar");
const btn_borrar = document.querySelector(".borrar");
const btn_guardar = document.querySelector(".boton-accion-guardar");
const btn_fitxes = document.querySelector(".enlace-volver");
const btnPDF = document.querySelector('.boton-accion-exportar');
const contImg = document.querySelector('.caja-arrastrar-soltar');
const dropZone = document.querySelector('.dropZone');
const tituloFicha = document.querySelector('.titulo-pagina');

//VARIABLES
let modoEdicion = false;
const rol = sessionStorage.getItem("rol");
const uid = sessionStorage.getItem("uid");
let map;
let marcador;
let latDef = 41.386978;  
let longDef = 2.170054; 
//leo el tab y el id de la URL para saber qué ficha cargar
const params = new URLSearchParams(window.location.search);
const tab = params.get('tab');
const id = params.get('id');
// Determinamos la colección de Firestore según el tab
let coleccio;
if (tab === 'sector') coleccio = 'sectors';
else if (tab === 'jaciment') coleccio = 'jaciments';
else if (tab === 'ue') coleccio = 'unitats_estratigrafiques';

/*
 * INIT MAPA
 * Inicializa el mapa Leaflet o actualiza su posición si ya existe.
 * Si el mapa ya existe, mueve la vista y el marcador a las nuevas coordenadas.
 * Si no existe, crea el mapa desde cero.
 * Parámetros:
 *   - lat: si es 0 o vacío usa latDef
 *   - long: si es 0 o vacío usa longDef
 */  
const initMapa = function(lat, long) {
    const latNum = parseFloat(lat) || latDef;
    const longNum = parseFloat(long) || longDef;
    
    if (map) {
        map.setView([latNum, longNum], 12);//Leaflet -> setView([latitud,longitud],zoom)
        if (marcador) {
            marcador.setLatLng([latNum, longNum]); // mover marcador existente
        } else {
            marcador = L.marker([latNum, longNum]).addTo(map); // crear si no existe
        }
    } else {
        map = L.map('map').setView([latNum, longNum], 12);
        L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
            attribution: '&copy; OpenStreetMap contributors'
        }).addTo(map);
        marcador = L.marker([latNum, longNum]).addTo(map);
    }
};

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

const cargarImg = async function(dades){
    document.querySelector('.imatges').style.display = 'block';
    if (dades.imatges_urls && dades.imatges_urls.length > 0) {
        contImg.textContent = "";
        dades.imatges_urls.forEach(url => {
            const imatge = crearImatge(url, async function(urlEliminar) {
                // Eliminar de Firestore
                await db.collection(coleccio).doc(id).update({
                    imatges_urls: firebase.firestore.FieldValue.arrayRemove(urlEliminar)
                });
            });
            dropZone.appendChild(imatge);
        });
    };
};

const cargarDescrip = function(dades, idDesc){
    const divDescrip = crearDiv('campo-area-texto');
    const labelDescrip = document.createElement('label');
    labelDescrip.textContent = "Descripció";
    const inputDescrip = document.createElement('textarea');
    inputDescrip.textContent = dades.descripcio;
    inputDescrip.disabled = true;
    inputDescrip.id=idDesc;
    divDescrip.appendChild(labelDescrip);
    divDescrip.appendChild(inputDescrip);
    colIzq.appendChild(divDescrip);
};

/*
 * CARGAR FICHA
 * Función principal que carga los datos de la ficha desde Firestore
 * y los pinta en el formulario según el tab (sector, jaciment o ue).
 * También controla los permisos de modificar/borrar según el rol del usuario.
 */
const cargarFicha = async function() {
    try {    
        const doc = await db.collection(coleccio).doc(id).get();
        
        if (!doc.exists) {
            console.error("Document no trobat");
            return;
        }
        
        const dades = doc.data();
        
        if (tab === 'sector') {
            tituloFicha.textContent = "Sector";
            //COLUMNA IZQUIERDA
            //nombre y yacimiento
            const divNom = crearDiv('grupo-rejilla-interno');
            divNom.appendChild(crearCampo("campo-formulario-base","Nom",dades.nom, "i-sec-nom"));
            divNom.appendChild(crearCampo("campo-formulario-base","Codi",dades.codi_sector, "i-sec-codi"));
            divNom.appendChild(crearCampo("campo-formulario-base","Jaciment",dades.codi_jaciment,"i-sec-codiJac")); 
            colIzq.appendChild(divNom);

            //descripcion
            cargarDescrip(dades, "i-sec-descr");

            //COLUMNA DERECHA
            //imágenes       
            cargarImg(dades);

        }else if (tab === 'jaciment'){
            tituloFicha.textContent = "Jaciment";
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
            labelAltitud.textContent = "Longitud x Latitud x Profunditat";
            const divMedidas = crearDiv('campo-doble-medida');
            // x longitud, y latitud, z profundidad
            const longitud = document.createElement('input');
            longitud.type = 'number';
            longitud.value = dades.coordenada_x || '';
            longitud.disabled = true;
            longitud.id = "i-jac-long";
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
            divMedidas.appendChild(longitud);
            divMedidas.appendChild(latitud);
            divMedidas.appendChild(profundidad);
            divCoor.appendChild(labelCoord);
            divCoor.appendChild(labelAltitud);
            divCoor.appendChild(divMedidas);
            colIzq.appendChild(divCoor);

            //descripcion
            cargarDescrip(dades, "i-jac-descr");

            //cargo tecnics y marco los que ya son editores
            const titEditors = crearDiv('titulo-bloque-interno');
            titEditors.textContent = 'Editors assignats';
            colIzq.appendChild(titEditors);

            const contenedorEditors = crearDiv('contenedor-checkbox-pildora');
            contenedorEditors.id = 'contenedor-editors';
            colIzq.appendChild(contenedorEditors);

            const usuaris = await db.collection('usuaris').where('rol', '==', 'tecnic').get();
            usuaris.forEach(doc => {
                const u = doc.data();
                const label = document.createElement('label');
                label.classList.add('etiqueta-checkbox-pildora');
                
                const checkbox = document.createElement('input');
                checkbox.type = 'checkbox';
                checkbox.value = u.email;
                //marco si ya es editor
                if (dades.editors && dades.editors.includes(u.email)) {
                    checkbox.checked = true;
                }
                checkbox.disabled = true; //bloqueado hasta que pulse modificar
                
                const span = document.createElement('span');
                span.textContent = u.nom;
                
                label.appendChild(checkbox);
                label.appendChild(span);
                contenedorEditors.appendChild(label);
            });

            //COLUMNA DERECHA      
            //imágenes        
            cargarImg(dades);

            //mapa
            const titMAP = crearDiv('titulo-bloque-interno');
            titMAP.textContent="Map";
            colDcha.appendChild(titMAP);
            const contMapa = crearDiv('container-map');
            contMapa.id = 'map';
            colDcha.appendChild(contMapa);
            initMapa(latitud.value, longitud.value);
            longitud.addEventListener('input', function() {
                initMapa(latitud.value, longitud.value);
            });
            latitud.addEventListener('input', function() {
                initMapa(latitud.value, longitud.value);
            });

        }else if(tab === 'ue'){
            tituloFicha.textContent = "Unitat estratigràfica";
            //COLUMNA IZQUIERDA
            //info basica
            const divInfo = crearDiv('grupo-rejilla-interno');
            divInfo.appendChild(crearCampo("campo-formulario-base","UE",dades.codi_ue,"i-ue-codi")); //ok
            divInfo.appendChild(crearCampo("campo-formulario-base","Sector",dades.codi_sector,"i-ue-codiSec")); //ok
            divInfo.appendChild(crearCampo("campo-formulario-base","Datacio",dades.cronologia,"i-ue-cronolog")); //ok
            divInfo.appendChild(crearCampo("campo-formulario-base","Estat conservacio",dades.estat_conservacio,"i-ue-estado")); //ok
            divInfo.appendChild(crearCampo("campo-formulario-base","Tipus UE",dades.tipus_ue,"i-ue-tipus")); //ok
            divInfo.appendChild(crearCampo("campo-formulario-base","Textura",dades.textura,"i-ue-text")); //ok
            divInfo.appendChild(crearCampo("campo-formulario-base","Color",dades.color,"i-ue-color")); //ok
            divInfo.appendChild(crearCampo("campo-formulario-base","Material",dades.material,"i-ue-mat")); //ok
            divInfo.appendChild(crearCampo("campo-formulario-base", "Registrat per", dades.nom_registrat || dades.registrat_per || 'Sense registre', "i-ue-person")); //ok
            //convierto fecha Timestamp a fecha legible
            const dataLlegible = dades.data ? dades.data.toDate().toLocaleDateString('ca-ES') : 'Sense data';
            divInfo.appendChild(crearCampo("campo-formulario-base","Data de creació",dataLlegible,"i-ue-data"));
            colIzq.appendChild(divInfo);
            
            //descripcion
            cargarDescrip(dades, "i-ue-descr");
            
            //COLUMNA DERECHA
            //relaciones
            
            //muestro campos
            document.querySelector('.relacions').style.display = 'block';
            //relleno valores si existen
            if (dades.relacions) {
                dades.relacions.forEach(rel => {
                    const input = document.querySelector(`.rel-${rel.tipus}`);
                    if (input) input.value = rel.desti;
                });
            }

            //imágenes        
            cargarImg(dades);
        }
        if (rol !== 'director') {
            if (tab === 'jaciment') {
                //técnico no puede modificar ni borrar jaciments
                btn_modificar.style.display = 'none';
                btn_borrar.style.display = 'none';
                btn_guardar.style.display = 'none';
            } else if (tab === 'ue') {
                //para UE, solo puede modificar/borrar si lo ha creado él
                if (dades.registrat_per !== uid) {
                    btn_modificar.style.display = 'none';
                    btn_borrar.style.display = 'none';
                    btn_guardar.style.display = 'none';
                }
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

/*
 * MODIFICAR
 * Activa el modo edición y desbloquea todos los inputs excepto
 * los campos de solo lectura (fecha y persona que registró).
 */
btn_modificar.addEventListener("click", function(){
    modoEdicion = true;
    btn_modificar.classList.add('actiu');
    //desbloquear todos los inputs
    document.querySelectorAll('.bloque-pestana input, .bloque-pestana textarea').forEach(camp => {
        //los campos de fecha y presona no se modificn, el resto si
        if (camp.id !== 'i-ue-data' && camp.id !== 'i-ue-person') {
            camp.disabled = false;
        }
    });
    
});

/*
 * BORRAR
 * Pide confirmación y elimina el documento de Firestore.
 * Después redirige a la librería.
 */
btn_borrar.addEventListener('click', async function() {
    const confirmacio = confirm("Estàs segur que vols esborrar aquesta fitxa? Aquesta acció no es pot desfer.");
    if (!confirmacio) return;

    try {
        await db.collection(coleccio).doc(id).delete();
        alert("Fitxa esborrada correctament");
        window.location.assign("libreria.html");
    } catch (error) {
        console.error("Error esborrant:", error);
        alert("Error en esborrar la fitxa");
    }
});

/*
 * GUARDAR
 * Recoge los valores actuales de los inputs y actualiza el documento en Firestore.
 * Después sincroniza con Oracle y bloquea los inputs otra vez.
 */
btn_guardar.addEventListener('click', async function() {
    modoEdicion = false;
    btn_modificar.classList.remove('actiu');
    try {
        // Recogemos los valores actuales de los inputs
        let dadesActualitzades = {};

        if (tab === 'sector') {
            dadesActualitzades = {
                nom: document.getElementById('i-sec-nom').value,
                codi_sector: document.getElementById('i-sec-codi').value,
                codi_jaciment: document.getElementById('i-sec-codiJac').value,
                descripcio: document.getElementById('i-sec-descr').value,
                data: firebase.firestore.Timestamp.now(),
                sincronitzat: false
            };
        } else if (tab === 'jaciment') {
            // Recogemos los emails de los técnicos marcados como editores
            const editors = Array.from(
                document.querySelectorAll('#contenedor-editors input:checked')
            ).map(cb => cb.value);

            dadesActualitzades = {
                nom: document.getElementById('i-jac-nom').value,
                codi_jaciment: document.getElementById('i-jac-codi').value,
                director: document.getElementById('i-jac-director').value,
                coordenada_x: document.getElementById('i-jac-long').value,
                coordenada_y: document.getElementById('i-jac-lat').value,
                coordenada_z: document.getElementById('i-jac-prof').value,
                descripcio: document.getElementById('i-jac-descr').value,
                editors: editors,
                data: firebase.firestore.Timestamp.now(),
                sincronitzat: false
            };
        } else if (tab === 'ue') {
            
            dadesActualitzades = {
                codi_ue: document.getElementById('i-ue-codi').value,
                codi_sector: document.getElementById('i-ue-codiSec').value,
                data: firebase.firestore.Timestamp.now(),
                tipus_ue: document.getElementById('i-ue-tipus').value,
                textura: document.getElementById('i-ue-text').value,
                color: document.getElementById('i-ue-color').value,
                material: document.getElementById('i-ue-mat').value,
                cronologia: document.getElementById('i-ue-cronolog').value,
                estat_conservacio: document.getElementById('i-ue-estado').value,
                descripcio: document.getElementById('i-ue-descr').value,
                sincronitzat: false,
                //recogemos las relaciones y construimos el array
                //pero con el filter solo guardamos las que tienen valor
                relacions: [
                    { tipus: 'igual_a', desti: document.querySelector('.rel-igual-a').value },
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
        sincronitzar(); //llamo a la función para actualizar bd
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

/*
 * EXPORTAR PDF
 * Abre el diálogo de impresión del navegador.
 * El CSS tiene un bloque @media print que oculta el navbar y los botones.
 */
btnPDF.addEventListener('click', function() {
    window.print();
});

//bloque arrastrar imágenes
dropZone.addEventListener("dragover", (event) => {
    event.preventDefault();
});
dropZone.addEventListener("drop", async (event) => {
    event.preventDefault();
    const files = event.dataTransfer.files;
    Array.from(files).forEach(async file => {
        if (file.type.startsWith("image/")) {
            //preview temporal mientras se sube
            const reader = new FileReader();
            reader.onload = (e) => {
                const imatge = crearImatge(e.target.result, null); // null porque aún no tiene URL de S3
                imatge.dataset.temporal = 'true';//marco como temporal
                dropZone.appendChild(imatge);
            };
            reader.readAsDataURL(file);
            //subo a S3
            if (id) {
                const publicUrl = await subirImatge(file, id, coleccio);
                //actualizo la imagen temporal con la URL real
                if (publicUrl) {
                    const imatgesTemporal = dropZone.querySelector('[data-temporal="true"]');
                    if (imatgesTemporal) {
                        imatgesTemporal.querySelector('img').src = publicUrl;
                        imatgesTemporal.removeAttribute('data-temporal');
                    }
                }
            }
        }
    });
});