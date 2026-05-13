//DOM
const contenedor = document.getElementById('contenedor-fichas');
const filtroTipo = document.getElementById('filtro-tipo');
const filtroBuscar = document.querySelector('.barra-filtros input[type="text"]');
const filtroFecha = document.getElementById('filtro-fecha');

//VARIABLES
let totesFitxes = []; //array con todas las fichas cargadas. Los filtros trabajan sobre este array
const rol = sessionStorage.getItem("rol");
const emailUsuari = sessionStorage.getItem("email");

/*
 * CREAR FICHA
 * Crea un elemento HTML con la información de una ficha (jaciment, sector o UE)
 * y lo añade al contenedor de la librería.
 * Al hacer click navega a la página de detalle pasando el tab y el id por la URL.
 */
const crearFicha = function(id, titulo, subtitulo, categoria, infoDreta, tab) {
    const ficha = document.createElement('div');
    ficha.classList.add('ficha-lista');

    const divIzq = document.createElement('div');

    const divTitulo = document.createElement('div');
    divTitulo.classList.add('titulo-ficha');
    divTitulo.textContent = titulo;

    const divSubtitulo = document.createElement('div');
    divSubtitulo.classList.add('subtitulo-ficha');
    divSubtitulo.textContent = subtitulo;
    
    //etiqueta de color según la categoría
    const etiqueta = document.createElement('span');
    etiqueta.classList.add('etiqueta-categoria', categoria);
    etiqueta.textContent = categoria === 'jaciment' ? 'Jaciment' : categoria === 'sector' ? 'Sector' : 'UE';

    divIzq.appendChild(divTitulo);
    divIzq.appendChild(divSubtitulo);
    divIzq.appendChild(etiqueta);

    const divDcha = document.createElement('div');
    divDcha.classList.add('datos-ficha-derecha');
    divDcha.textContent = infoDreta;

    ficha.appendChild(divIzq);
    ficha.appendChild(divDcha);
    
    //al hacer click navega al detalle con el tab y el id del documento
    ficha.addEventListener('click', function() {
        window.location.assign(`detail.html?tab=${tab}&id=${id}`);
    });

    contenedor.appendChild(ficha);
};

/*
 * PINTAR FICHAS
 * Limpia el contenedor y pinta las fichas del array que recibe.
 * Se usa tanto para la carga inicial como para los filtros.
 */
const pintarFitxes = function(fitxes) {
    contenedor.replaceChildren(); // limpio antes de pintar
    fitxes.forEach(f => {
        crearFicha(f.id, f.nom, f.subtitol, f.categoria, f.info, f.tab);
    });
};

/*
 * CARGAR FICHAS
 * Carga todos los jaciments, sectors y UEs de Firestore y los guarda en totesFitxes.
 * Solo se ejecuta una vez al cargar la página. 
 * 
 * Si el usuario es técnico, filtra para mostrar solo las fichas
 * de los jaciments donde está asignado como editor.
 */
const cargarFichas = async function() {
    try {
        //CARGO YACIMIENTOS
        const jaciments = await db.collection('jaciments').get();
        jaciments.forEach(doc => {
            const d = doc.data();
            //id, titulo, subtitulo, categoria, infoDreta, tab
            totesFitxes.push({
                id: doc.id,
                tab: 'jaciment',
                nom: d.nom,
                subtitol: d.descripcio,
                categoria: 'jaciment',
                info: `Director/a: ${d.director}`,
                data: d.data || null,
                editors: d.editors || [],
                codi_jaciment: d.codi_jaciment
            });
            
        });

        //CARGO SECTORES
        const sectors = await db.collection('sectors').get();
        sectors.forEach(doc => {
            const d = doc.data();
            //id, titulo, subtitulo, categoria, infoDreta, tab
            totesFitxes.push({
                id: doc.id,
                tab: 'sector',
                nom: d.nom,
                subtitol: d.codi_sector,
                categoria: 'sector',
                info: `Jaciment: ${d.codi_jaciment}`,
                data: d.data || null,
                codi_jaciment: d.codi_jaciment,
                codi_sector: d.codi_sector //campo para el filtro
            });
        });

        //CARGO UEs
        const ues = await db.collection('unitats_estratigrafiques').get();
        ues.forEach(doc => {
            const d = doc.data();
            //id, titulo, subtitulo, categoria, infoDreta, tab
            totesFitxes.push({
                id: doc.id,
                tab: 'ue',
                nom: d.codi_ue,
                subtitol: d.tipus_ue,
                categoria: 'ue',
                info: `Sector: ${d.codi_sector || 'No hay sector'}`,
                data: d.data || null,
                codi_sector: d.codi_sector
            });
        });

        // FILTRAR SEGÚN ROL
        if (rol === 'tecnic') {
            // Primero encontramos los jaciments donde es editor
            const jacimentsPermesos = totesFitxes
                .filter(f => f.tab === 'jaciment' && f.editors.includes(emailUsuari))
                .map(f => f.codi_jaciment);

            // Filtramos las fichas
            totesFitxes = totesFitxes.filter(f => {
                if (f.tab === 'jaciment') {
                    return jacimentsPermesos.includes(f.codi_jaciment);
                }
                if (f.tab === 'sector') {
                    return jacimentsPermesos.includes(f.codi_jaciment);
                }
                if (f.tab === 'ue') {
                    // Para UE necesitamos saber el jaciment del sector
                    const sector = totesFitxes.find(s => 
                        s.tab === 'sector' && s.codi_sector === f.codi_sector
                    );
                    return sector && jacimentsPermesos.includes(sector.codi_jaciment);
                }
                return true;
            });
        }
        //pintamos todo al final
        pintarFitxes(totesFitxes);

    } catch (error) {
        console.error("Error carregant les fitxes:", error);
    }
};

//FILTROS
const aplicarFiltres = function() {
    let resultat = [...totesFitxes]; //copio el array original
    
    //filtro por tipo
    const tipus = filtroTipo.value.toLowerCase();
    if (tipus !== 'totes') {
        resultat = resultat.filter(f => f.tab === tipus);
    }

    //filtro por nombre
    const text = filtroBuscar.value.toLowerCase();
    if (text.length > 0) {
        resultat = resultat.filter(f => f.nom && f.nom.toLowerCase().includes(text));
    }

    //filtro por fecha/orden
    const ordre = filtroFecha.value;
    if (ordre === 'recent') {
        resultat.sort((a, b) => {
            if (!a.data && !b.data) return 0;
            if (!a.data) return 1;
            if (!b.data) return -1;
            return b.data.toMillis() - a.data.toMillis();
        });
    } else if (ordre === 'antic') {
        resultat.sort((a, b) => {
            if (!a.data && !b.data) return 0;
            if (!a.data) return 1;
            if (!b.data) return -1;
            return a.data.toMillis() - b.data.toMillis();
        });
    }
    pintarFitxes(resultat);
};

cargarFichas();

//filtro por tipo
filtroTipo.addEventListener('change', function() {
    aplicarFiltres();
});

//filtro buscador por nombre
filtroBuscar.addEventListener('input', function() {
    aplicarFiltres();
});

filtroFecha.addEventListener('change', function() {
    aplicarFiltres();
});
