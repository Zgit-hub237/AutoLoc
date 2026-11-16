/*
 * AutoLoc console: single page application without framework.
 * It talks to the REST API /api/console/** with HTTP Basic credentials kept for the browser session.
 */
(() => {
    'use strict';

    // ------------------------------------------------------------------ Formatting
    const money = new Intl.NumberFormat('fr-FR', {style: 'currency', currency: 'EUR'});
    const integer = new Intl.NumberFormat('fr-FR');
    const esc = (v) => String(v ?? '').replace(/[&<>"']/g, (c) =>
        ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[c]));
    const parseDate = (iso) => {
        if (!iso) return null;
        const [y, m, d] = iso.split('-').map(Number);
        return new Date(y, m - 1, d);
    };
    const fmtDate = (iso) => iso
        ? parseDate(iso).toLocaleDateString('fr-FR', {day: '2-digit', month: 'short', year: 'numeric'})
        : '';
    const fmtMoney = (v) => (v === null || v === undefined || v === '') ? '' : money.format(Number(v));
    const days = (a, b) => Math.round((parseDate(b) - parseDate(a)) / 86400000) + 1;
    const today = () => {
        const d = new Date();
        return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
    };

    const LABELS = {
        CITADINE: 'Citadine', BERLINE: 'Berline', SUV: 'SUV', UTILITAIRE: 'Utilitaire',
        DISPONIBLE: 'Disponible', LOUE: 'Loué', MAINTENANCE: 'En maintenance',
        AGENT: 'Agent', MANAGER: 'Manager',
        EN_ATTENTE: 'En attente', CONFIRMEE: 'Confirmée', ANNULEE: 'Annulée', TERMINEE: 'Terminée',
        CARTE: 'Carte bancaire', ESPECES: 'Espèces', VIREMENT: 'Virement'
    };
    const TONES = {
        DISPONIBLE: 'success', LOUE: 'info', MAINTENANCE: 'warning',
        EN_ATTENTE: 'warning', CONFIRMEE: 'info', ANNULEE: 'danger', TERMINEE: 'success',
        MANAGER: 'info'
    };
    const label = (v) => LABELS[v] ?? v ?? '';
    const pill = (v) => v ? `<span class="badge ${TONES[v] ? 'tone-' + TONES[v] : ''}">${esc(label(v))}</span>` : '';
    const two = (primary, secondary) =>
        `<span class="primary">${esc(primary)}</span>${secondary ? `<span class="secondary">${esc(secondary)}</span>` : ''}`;
    const dash = '<span class="muted">-</span>';

    // ------------------------------------------------------------------ Icons (simple strokes)
    const ICON = {
        dashboard: '<path d="M3 13h8V3H3zM13 21h8V11h-8zM3 21h8v-6H3zM13 3v6h8V3z"/>',
        car: '<path d="M5 17h14M6 17v2M18 17v2M4 13l2-6h12l2 6v4H4z"/><circle cx="8" cy="14.5" r=".6"/><circle cx="16" cy="14.5" r=".6"/>',
        tool: '<path d="M14.7 6.3a4 4 0 0 0-5.4 5.4L3 18l3 3 6.3-6.3a4 4 0 0 0 5.4-5.4l-2.6 2.6-2.4-.6-.6-2.4z"/>',
        box: '<path d="M21 8l-9-5-9 5 9 5 9-5zM3 8v8l9 5 9-5V8M12 13v8"/>',
        building: '<path d="M4 21V5l8-2v18M12 9h8v12M8 8h.01M8 12h.01M8 16h.01M16 13h.01M16 17h.01M2 21h20"/>',
        users: '<path d="M16 20v-1a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v1"/><circle cx="9" cy="8" r="4"/><path d="M22 20v-1a4 4 0 0 0-3-3.9M16 4.1a4 4 0 0 1 0 7.8"/>',
        user: '<circle cx="12" cy="8" r="4"/><path d="M4 21v-1a6 6 0 0 1 6-6h4a6 6 0 0 1 6 6v1"/>',
        calendar: '<rect x="3" y="5" width="18" height="16" rx="2"/><path d="M16 3v4M8 3v4M3 10h18"/>',
        file: '<path d="M14 3H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z"/><path d="M14 3v6h6M8 13h8M8 17h5"/>',
        card: '<rect x="2" y="5" width="20" height="14" rx="2"/><path d="M2 10h20M6 15h4"/>',
        search: '<circle cx="11" cy="11" r="7"/><path d="M21 21l-4.3-4.3"/>'
    };
    const icon = (name) => `<svg viewBox="0 0 24 24" fill="none" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">${ICON[name]}</svg>`;

    // ------------------------------------------------------------------ Resources (one per entity)
    const RESOURCES = {
        vehicules: {
            title: 'Véhicules', singular: 'véhicule', feminine: false, group: 'Flotte', icon: 'car',
            filter: {key: 'statut', enum: 'StatutVehicule', label: 'Tous les statuts'},
            option: (v) => `${v.marque} ${v.modele} · ${v.immatriculation}`,
            columns: [
                {key: 'immatriculation', label: 'Immatriculation', render: (v) => `<span class="mono">${esc(v.immatriculation)}</span>`},
                {key: 'marque', label: 'Modèle', render: (v) => two(`${v.marque} ${v.modele}`, label(v.categorie))},
                {key: 'agenceNom', label: 'Agence', render: (v) => v.agenceNom ? esc(v.agenceNom) : dash},
                {key: 'equipements', label: 'Équipements', sortable: false,
                    render: (v) => v.equipements.length ? v.equipements.map((e) => `<span class="tag">${esc(e)}</span>`).join('') : dash},
                {key: 'tarifJournalier', label: 'Tarif / jour', right: true, render: (v) => `<span class="num">${fmtMoney(v.tarifJournalier)}</span>`},
                {key: 'statut', label: 'Statut', render: (v) => pill(v.statut)}
            ],
            fields: [
                {key: 'immatriculation', label: 'Immatriculation', required: true, max: 20, placeholder: 'AB-123-CD'},
                {key: 'statut', label: 'Statut', type: 'enum', enum: 'StatutVehicule', required: true, default: 'DISPONIBLE'},
                {key: 'marque', label: 'Marque', required: true, max: 50},
                {key: 'modele', label: 'Modèle', required: true, max: 50},
                {key: 'categorie', label: 'Catégorie', type: 'enum', enum: 'CategorieVehicule', required: true},
                {key: 'tarifJournalier', label: 'Tarif journalier (€)', type: 'money', required: true},
                {key: 'agenceId', label: 'Agence de rattachement', type: 'ref', ref: 'agences', full: true},
                {key: 'equipementIds', label: 'Équipements', type: 'multi', ref: 'equipements', full: true,
                    help: 'Association @ManyToMany : table vehicule_equipement'}
            ],
            deleteNote: 'Les lignes de vehicule_equipement sont retirées automatiquement. La suppression est refusée si des maintenances ou des réservations y font référence.'
        },
        equipements: {
            title: 'Équipements', singular: 'équipement', group: 'Flotte', icon: 'box',
            option: (e) => e.libelle,
            columns: [
                {key: 'libelle', label: 'Libellé', render: (e) => `<span class="primary">${esc(e.libelle)}</span>`},
                {key: 'nbVehicules', label: 'Véhicules équipés', right: true, render: (e) => `<span class="num">${e.nbVehicules}</span>`}
            ],
            fields: [{key: 'libelle', label: 'Libellé', required: true, max: 100, full: true}],
            deleteNote: 'L’équipement est d’abord retiré des véhicules qui le possèdent (côté propriétaire de la relation).'
        },
        maintenances: {
            title: 'Maintenances', singular: 'maintenance', feminine: true, group: 'Flotte', icon: 'tool',
            columns: [
                {key: 'vehiculeLabel', label: 'Véhicule', render: (m) => esc(m.vehiculeLabel)},
                {key: 'description', label: 'Intervention', render: (m) => `<span class="primary">${esc(m.description)}</span>`},
                {key: 'dateDebut', label: 'Début', render: (m) => fmtDate(m.dateDebut)},
                {key: 'dateFin', label: 'Fin', render: (m) => m.dateFin ? fmtDate(m.dateFin) : '<span class="badge tone-warning">En cours</span>'}
            ],
            fields: [
                {key: 'vehiculeId', label: 'Véhicule', type: 'ref', ref: 'vehicules', required: true, full: true},
                {key: 'description', label: 'Description', type: 'textarea', required: true, full: true},
                {key: 'dateDebut', label: 'Date de début', type: 'date', required: true, default: today},
                {key: 'dateFin', label: 'Date de fin', type: 'date', help: 'Vide = maintenance en cours'}
            ]
        },
        agences: {
            title: 'Agences', singular: 'agence', feminine: true, group: 'Réseau', icon: 'building',
            option: (a) => `${a.nom} · ${a.ville}`,
            columns: [
                {key: 'nom', label: 'Agence', render: (a) => two(a.nom, a.adresse)},
                {key: 'ville', label: 'Ville', render: (a) => esc(a.ville)},
                {key: 'telephone', label: 'Téléphone', render: (a) => a.telephone ? `<span class="num">${esc(a.telephone)}</span>` : dash},
                {key: 'nbVehicules', label: 'Véhicules', right: true, render: (a) => `<span class="num">${a.nbVehicules}</span>`},
                {key: 'nbEmployes', label: 'Employés', right: true, render: (a) => `<span class="num">${a.nbEmployes}</span>`}
            ],
            fields: [
                {key: 'nom', label: 'Nom', required: true, max: 100, full: true},
                {key: 'adresse', label: 'Adresse', required: true, max: 255, full: true},
                {key: 'ville', label: 'Ville', required: true, max: 50},
                {key: 'telephone', label: 'Téléphone', max: 20}
            ],
            deleteNote: 'Aucune cascade : la suppression est refusée tant que des véhicules ou des employés y sont rattachés.'
        },
        employes: {
            title: 'Employés', singular: 'employé', group: 'Réseau', icon: 'users',
            columns: [
                {key: 'nom', label: 'Nom', render: (e) => `<span class="primary">${esc(e.prenom)} ${esc(e.nom)}</span>`},
                {key: 'role', label: 'Rôle', render: (e) => pill(e.role)},
                {key: 'agenceNom', label: 'Agence', render: (e) => e.agenceNom ? esc(e.agenceNom) : dash}
            ],
            filter: {key: 'role', enum: 'RoleEmploye', label: 'Tous les rôles'},
            fields: [
                {key: 'prenom', label: 'Prénom', required: true, max: 50},
                {key: 'nom', label: 'Nom', required: true, max: 50},
                {key: 'role', label: 'Rôle', type: 'enum', enum: 'RoleEmploye', required: true},
                {key: 'agenceId', label: 'Agence', type: 'ref', ref: 'agences'}
            ]
        },
        clients: {
            title: 'Clients', singular: 'client', group: 'Commercial', icon: 'user',
            option: (c) => `${c.prenom} ${c.nom} · ${c.email}`,
            columns: [
                {key: 'nom', label: 'Client', render: (c) => two(`${c.prenom} ${c.nom}`, c.email)},
                {key: 'telephone', label: 'Téléphone', render: (c) => c.telephone ? `<span class="num">${esc(c.telephone)}</span>` : dash},
                {key: 'numPermis', label: 'Permis', render: (c) => `<span class="mono">${esc(c.numPermis)}</span>`},
                {key: 'dateInscription', label: 'Inscription', render: (c) => fmtDate(c.dateInscription)},
                {key: 'nbReservations', label: 'Réservations', right: true, render: (c) => `<span class="num">${c.nbReservations}</span>`}
            ],
            fields: [
                {key: 'prenom', label: 'Prénom', required: true, max: 50},
                {key: 'nom', label: 'Nom', required: true, max: 50},
                {key: 'email', label: 'Email', type: 'email', required: true, max: 100, full: true},
                {key: 'telephone', label: 'Téléphone', max: 20},
                {key: 'numPermis', label: 'N° de permis', required: true, max: 30},
                {key: 'dateInscription', label: 'Date d’inscription', type: 'date', default: today}
            ],
            deleteNote: 'Cascade PERSIST seulement : les réservations sont conservées, la suppression est donc refusée si le client en possède.'
        },
        reservations: {
            title: 'Réservations', singular: 'réservation', feminine: true, group: 'Commercial', icon: 'calendar',
            filter: {key: 'statut', enum: 'StatutReservation', label: 'Tous les statuts'},
            option: (r) => `R-${r.id} · ${r.clientNom} · ${fmtDate(r.dateDebut)}`,
            defaultSort: {key: 'dateDebut', dir: -1},
            columns: [
                {key: 'id', label: 'N°', render: (r) => `<span class="mono">R-${r.id}</span>`},
                {key: 'clientNom', label: 'Client', render: (r) => `<span class="primary">${esc(r.clientNom)}</span>`},
                {key: 'vehiculeLabel', label: 'Véhicule', render: (r) => esc(r.vehiculeLabel)},
                {key: 'dateDebut', label: 'Période',
                    render: (r) => two(`${fmtDate(r.dateDebut)} → ${fmtDate(r.dateFin)}`, `${days(r.dateDebut, r.dateFin)} jour(s)`)},
                {key: 'statut', label: 'Statut', render: (r) => pill(r.statut)},
                {key: 'contratId', label: 'Contrat', render: (r) => r.contratId ? `<span class="mono">C-${r.contratId}</span>` : dash}
            ],
            fields: [
                {key: 'clientId', label: 'Client', type: 'ref', ref: 'clients', required: true, full: true},
                {key: 'vehiculeId', label: 'Véhicule', type: 'ref', ref: 'vehicules', required: true, full: true},
                {key: 'dateDebut', label: 'Début', type: 'date', required: true, default: today},
                {key: 'dateFin', label: 'Fin', type: 'date', required: true},
                {key: 'statut', label: 'Statut', type: 'enum', enum: 'StatutReservation', required: true, default: 'EN_ATTENTE'}
            ],
            deleteNote: 'Cascade ALL : le contrat lié est supprimé, puis ses paiements (cascade + orphanRemoval).'
        },
        contrats: {
            title: 'Contrats', singular: 'contrat', group: 'Commercial', icon: 'file',
            option: (c) => `C-${c.id} · ${fmtMoney(c.montantTotal)}`,
            columns: [
                {key: 'id', label: 'N°', render: (c) => `<span class="mono">C-${c.id}</span>`},
                {key: 'reservationLabel', label: 'Réservation', render: (c) => esc(c.reservationLabel)},
                {key: 'dateSignature', label: 'Signature', render: (c) => fmtDate(c.dateSignature)},
                {key: 'montantTotal', label: 'Montant', right: true, render: (c) => `<span class="num">${fmtMoney(c.montantTotal)}</span>`},
                {key: 'totalPaye', label: 'Encaissé', render: (c) => {
                    const pct = Number(c.montantTotal) > 0 ? Math.min(100, Math.round(100 * c.totalPaye / c.montantTotal)) : 0;
                    return `<div class="progress" title="${esc(fmtMoney(c.totalPaye))} sur ${c.nbPaiements} paiement(s)">
                        <div class="track"><div class="fill" style="width:${pct}%"></div></div><span class="pct">${pct} %</span></div>`;
                }},
                {key: 'valide', label: 'État', render: (c) => c.valide
                        ? '<span class="badge tone-success">Validé</span>' : '<span class="badge">Brouillon</span>'}
            ],
            fields: [
                {key: 'reservationId', label: 'Réservation', type: 'ref', ref: 'reservations', required: true, full: true,
                    help: 'Association @OneToOne : une réservation a au plus un contrat (contrainte UNIQUE)'},
                {key: 'dateSignature', label: 'Date de signature', type: 'date', default: today},
                {key: 'montantTotal', label: 'Montant total (€)', type: 'money', required: true},
                {key: 'valide', label: 'Contrat validé', type: 'boolean', full: true}
            ],
            deleteNote: 'Cascade ALL + orphanRemoval : tous les paiements du contrat sont supprimés avec lui.'
        },
        paiements: {
            title: 'Paiements', singular: 'paiement', group: 'Commercial', icon: 'card',
            filter: {key: 'modePaiement', enum: 'ModePaiement', label: 'Tous les modes'},
            defaultSort: {key: 'datePaiement', dir: -1},
            columns: [
                {key: 'id', label: 'N°', render: (p) => `<span class="mono">P-${p.id}</span>`},
                {key: 'contratLabel', label: 'Contrat', render: (p) => esc(p.contratLabel)},
                {key: 'datePaiement', label: 'Date', render: (p) => fmtDate(p.datePaiement)},
                {key: 'modePaiement', label: 'Mode', render: (p) => `<span class="badge plain">${esc(label(p.modePaiement))}</span>`},
                {key: 'montant', label: 'Montant', right: true, render: (p) => `<span class="num primary">${fmtMoney(p.montant)}</span>`}
            ],
            fields: [
                {key: 'contratId', label: 'Contrat', type: 'ref', ref: 'contrats', required: true, full: true},
                {key: 'montant', label: 'Montant (€)', type: 'money', required: true},
                {key: 'datePaiement', label: 'Date', type: 'date', default: today},
                {key: 'modePaiement', label: 'Mode de paiement', type: 'enum', enum: 'ModePaiement', required: true, full: true}
            ]
        }
    };
    const GROUPS = ['Flotte', 'Réseau', 'Commercial'];
    const article = (res) => res.feminine ? 'la' : (/^[aeéiou]/i.test(res.singular) ? 'l’' : 'le');
    const newWord = (res) => res.feminine ? 'Nouvelle' : (/^[aeéiou]/i.test(res.singular) ? 'Nouvel' : 'Nouveau');

    // ------------------------------------------------------------------ State + API
    const AUTH_KEY = 'autoloc.console.auth';
    const state = {auth: null, me: null, enums: {}, data: {}, route: null, view: {}};
    const $ = (sel, root = document) => root.querySelector(sel);

    function readAuth() {
        try { return sessionStorage.getItem(AUTH_KEY); } catch { return state.auth; }
    }
    function writeAuth(value) {
        state.auth = value;
        try { value ? sessionStorage.setItem(AUTH_KEY, value) : sessionStorage.removeItem(AUTH_KEY); } catch { /* private mode */ }
    }

    class ApiError extends Error {
        constructor(status, message) { super(message); this.status = status; }
    }

    /** Calls /api + path. Without credentials (sign-up) no Authorization header is sent. */
    async function api(method, path, body, auth = state.auth) {
        const headers = {
            // Asks Spring Security for a plain 401 (no browser login popup)
            'X-Requested-With': 'XMLHttpRequest',
            'Accept': 'application/json'
        };
        if (auth) headers['Authorization'] = 'Basic ' + auth;
        if (body !== undefined) headers['Content-Type'] = 'application/json';
        let response;
        try {
            response = await fetch('/api' + path, {method, headers, body: body === undefined ? undefined : JSON.stringify(body)});
        } catch {
            throw new ApiError(0, 'Serveur injoignable : l’application Spring Boot est-elle démarrée ?');
        }
        if (response.status === 204) return null;
        const data = await response.json().catch(() => null);
        if (!response.ok) {
            const message = data?.message
                ?? {401: 'Email/identifiant ou mot de passe incorrect.', 403: 'Action non autorisée pour votre compte.', 404: 'Élément introuvable.'}[response.status]
                ?? `Erreur ${response.status}`;
            throw new ApiError(response.status, message);
        }
        return data;
    }

    // ------------------------------------------------------------------ Toasts + confirmation
    function toast(message, type = 'success') {
        const el = document.createElement('div');
        el.className = 'toast' + (type === 'error' ? ' error' : '');
        el.textContent = message;
        $('#toasts').appendChild(el);
        setTimeout(() => {
            el.classList.add('leaving');
            el.addEventListener('animationend', () => el.remove(), {once: true});
        }, type === 'error' ? 5500 : 3200);
    }

    function openLayer(layer) {
        layer.hidden = false;
        requestAnimationFrame(() => requestAnimationFrame(() => layer.classList.add('open')));
    }
    function closeLayer(layer) {
        layer.classList.remove('open');
        setTimeout(() => { layer.hidden = true; }, 280);
    }

    function confirmDialog(title, text, actionLabel = 'Supprimer') {
        const layer = $('#modal-layer');
        $('#modal-title').textContent = title;
        $('#modal-text').textContent = text;
        $('#modal-confirm').textContent = actionLabel;
        openLayer(layer);
        $('#modal-confirm').focus();
        return new Promise((resolve) => {
            const finish = (value) => {
                closeLayer(layer);
                layer.removeEventListener('click', onClick);
                document.removeEventListener('keydown', onKey);
                resolve(value);
            };
            const onClick = (e) => {
                if (e.target.closest('#modal-confirm')) finish(true);
                else if (e.target.closest('[data-cancel]')) finish(false);
            };
            const onKey = (e) => { if (e.key === 'Escape') finish(false); };
            layer.addEventListener('click', onClick);
            document.addEventListener('keydown', onKey);
        });
    }

    // ------------------------------------------------------------------ Login / logout
    function showPane(name) {
        $('#login-pane').hidden = name !== 'login';
        $('#signup-pane').hidden = name !== 'signup';
        const form = name === 'login' ? $('#login-form') : $('#signup-form');
        setTimeout(() => form.querySelector('input').focus(), 50);
    }

    function showLogin(message) {
        $('#app').hidden = true;
        $('#login').hidden = false;
        showPane('login');
        const err = $('#login-error');
        err.hidden = !message;
        err.textContent = message || '';
    }

    $('#login').addEventListener('click', (e) => {
        const link = e.target.closest('[data-pane]');
        if (!link) return;
        e.preventDefault();
        $('#login-error').hidden = true;
        $('#signup-error').hidden = true;
        showPane(link.dataset.pane);
    });

    const encode = (user, password) => btoa(unescape(encodeURIComponent(`${user}:${password}`)));

    /** Loads the session and opens the space matching the role: agency console or customer space. */
    async function start(auth) {
        const me = await api('GET', '/me', undefined, auth);
        writeAuth(auth);
        state.me = {
            username: me.username,
            admin: me.roles.includes('ADMIN'),
            client: me.roles.includes('CLIENT')
        };
        state.enums = await api('GET', '/enums');
        $('#login').hidden = true;
        $('#app').hidden = false;
        const role = $('#user-role');
        if (state.me.client) {
            const profil = await api('GET', '/client/profil');
            state.profil = profil;
            $('#user-name').textContent = `${profil.prenom} ${profil.nom}`;
            role.textContent = 'Client';
            role.className = 'badge tone-success';
            $('#sidebar-foot').textContent = 'Location de véhicules';
        } else {
            $('#user-name').textContent = me.username;
            role.textContent = state.me.admin ? 'Administrateur' : 'Consultation';
            role.className = 'badge ' + (state.me.admin ? 'tone-info' : '');
            $('#sidebar-foot').textContent = 'Gestion de l’agence';
        }
        renderNav();
        route();
        if (!state.me.client) {
            // Fill the menu counters in the background
            Object.keys(RESOURCES).forEach((key) => load(key).catch(() => {}));
        }
    }

    function busy(button, label) {
        button.disabled = true;
        const previous = button.textContent;
        button.innerHTML = `<span class="spinner"></span> ${esc(label)}`;
        return () => { button.disabled = false; button.textContent = previous; };
    }

    $('#signup-form').addEventListener('submit', async (e) => {
        e.preventDefault();
        const form = e.currentTarget;
        const err = $('#signup-error');
        const data = Object.fromEntries(new FormData(form).entries());
        const fail = (message) => { err.textContent = message; err.hidden = false; };
        const missing = ['prenom', 'nom', 'email', 'numPermis', 'motDePasse'].filter((k) => !data[k].trim());
        if (missing.length) return fail('Complétez tous les champs obligatoires.');
        if (data.motDePasse.length < 8) return fail('Le mot de passe doit contenir au moins 8 caractères.');
        if (data.motDePasse !== data.confirmation) return fail('Les deux mots de passe ne correspondent pas.');
        err.hidden = true;
        const done = busy(form.querySelector('button'), 'Création du compte');
        try {
            const {confirmation, ...body} = data;
            await api('POST', '/client/inscription', body, null);
            await start(encode(body.email.trim().toLowerCase(), body.motDePasse));
            form.reset();
            toast('Bienvenue ! Votre compte client est créé.');
        } catch (error) {
            fail(error.message);
        } finally {
            done();
        }
    });

    $('#login-form').addEventListener('submit', async (e) => {
        e.preventDefault();
        const form = e.currentTarget;
        const username = form.username.value.trim();
        const password = form.password.value;
        if (!username || !password) return showLogin('Saisissez l’identifiant et le mot de passe.');
        const done = busy(form.querySelector('button'), 'Connexion');
        try {
            await start(encode(username, password));
            form.reset();
        } catch (err) {
            showLogin(err.message);
        } finally {
            done();
        }
    });

    $('#logout').addEventListener('click', () => {
        writeAuth(null);
        state.me = null;
        state.data = {};
        // Leave nothing of the previous user's screens in the page
        $('#view').innerHTML = '';
        $('#drawer-form').innerHTML = '';
        $('#drawer-layer').hidden = true;
        state.profil = null;
        document.title = 'AutoLoc · Location de véhicules';
        history.replaceState(null, '', location.pathname);
        showLogin();
    });

    // ------------------------------------------------------------------ Navigation
    const CLIENT_ROUTES = {
        louer: {title: 'Louer un véhicule', icon: 'car', render: () => renderCatalogue()},
        locations: {title: 'Mes locations', icon: 'calendar', render: () => renderLocations()},
        profil: {title: 'Mon profil', icon: 'user', render: () => renderProfil()}
    };

    function renderNav() {
        if (state.me.client) {
            $('#nav').innerHTML = '<div class="nav-group">Espace client</div>'
                + Object.entries(CLIENT_ROUTES).map(([key, r]) =>
                    `<a href="#/${key}" data-route="${key}">${icon(r.icon)}<span>${r.title}</span></a>`).join('');
            return;
        }
        let html = '<div class="nav-group">Pilotage</div>'
            + `<a href="#/tableau-de-bord" data-route="tableau-de-bord">${icon('dashboard')}<span>Tableau de bord</span></a>`;
        for (const group of GROUPS) {
            html += `<div class="nav-group">${group}</div>`;
            for (const [key, res] of Object.entries(RESOURCES).filter(([, r]) => r.group === group)) {
                html += `<a href="#/${key}" data-route="${key}">${icon(res.icon)}<span>${res.title}</span><span class="count" data-count="${key}"></span></a>`;
            }
        }
        $('#nav').innerHTML = html;
    }

    function updateCounts() {
        for (const [key, rows] of Object.entries(state.data)) {
            const el = document.querySelector(`[data-count="${key}"]`);
            if (el) el.textContent = rows.length;
        }
    }

    function route() {
        if (!state.me) return;
        const name = location.hash.replace(/^#\/?/, '');
        if (state.me.client) {
            state.route = CLIENT_ROUTES[name] ? name : 'louer';
        } else {
            state.route = RESOURCES[name] ? name : 'tableau-de-bord';
        }
        document.querySelectorAll('#nav a').forEach((a) => a.classList.toggle('active', a.dataset.route === state.route));
        $('#app').classList.remove('nav-open');
        window.scrollTo({top: 0});
        if (state.me.client) CLIENT_ROUTES[state.route].render();
        else if (state.route === 'tableau-de-bord') renderDashboard();
        else renderResource(state.route);
    }
    window.addEventListener('hashchange', route);
    $('#menu-btn').addEventListener('click', () => $('#app').classList.toggle('nav-open'));
    $('#scrim').addEventListener('click', () => $('#app').classList.remove('nav-open'));

    function setTitle(crumb, title) {
        $('#crumb').textContent = crumb;
        $('#page-title').textContent = title;
        document.title = `${title} · AutoLoc`;
    }

    function handleError(err) {
        if (err.status === 401) {
            writeAuth(null);
            showLogin('Session expirée, reconnectez-vous.');
        } else {
            toast(err.message, 'error');
        }
    }

    async function load(key, force = false) {
        if (!state.data[key] || force) {
            state.data[key] = await api('GET', '/console/' + key);
            updateCounts();
        }
        return state.data[key];
    }

    // ------------------------------------------------------------------ Dashboard
    function countUp(el, target, format) {
        const start = performance.now();
        const duration = 700;
        const step = (now) => {
            const t = Math.min(1, (now - start) / duration);
            const eased = 1 - Math.pow(1 - t, 3);
            el.textContent = format(target * eased);
            if (t < 1) requestAnimationFrame(step);
        };
        requestAnimationFrame(step);
    }

    async function renderDashboard() {
        setTitle('Pilotage', 'Tableau de bord');
        const view = $('#view');
        view.innerHTML = `<div class="kpis">${'<div class="panel kpi"><div class="skeleton"><div class="bone" style="width:40%"></div><div class="bone" style="width:60%;height:22px;margin-top:12px"></div></div></div>'.repeat(4)}</div>`;
        let s;
        try {
            s = await api('GET', '/console/stats');
        } catch (err) { return handleError(err); }
        if (state.route !== 'tableau-de-bord') return;

        const vs = s.vehiculesParStatut;
        const totalV = Object.values(vs).reduce((a, b) => a + b, 0);
        const rs = s.reservationsParStatut;
        const maxR = Math.max(1, ...Object.values(rs));
        const fleetColors = {DISPONIBLE: '#12b76a', LOUE: '#2e5aac', MAINTENANCE: '#f79009'};
        const longDate = new Date().toLocaleDateString('fr-FR', {weekday: 'long', day: 'numeric', month: 'long', year: 'numeric'});

        view.innerHTML = `
            <div class="kpis">
                <div class="panel kpi"><div class="kpi-label">Véhicules</div><div class="kpi-value" data-count-to="${s.totaux.vehicules}">0</div>
                    <div class="kpi-sub"><strong>${vs.DISPONIBLE ?? 0}</strong> disponibles aujourd’hui</div></div>
                <div class="panel kpi"><div class="kpi-label">Réservations</div><div class="kpi-value" data-count-to="${s.totaux.reservations}">0</div>
                    <div class="kpi-sub"><strong>${s.prochainesReservations.length}</strong> en cours ou à venir</div></div>
                <div class="panel kpi"><div class="kpi-label">Clients</div><div class="kpi-value" data-count-to="${s.totaux.clients}">0</div>
                    <div class="kpi-sub"><strong>${s.totaux.agences}</strong> agences · <strong>${s.totaux.employes}</strong> employés</div></div>
                <div class="panel kpi"><div class="kpi-label">Montant encaissé</div><div class="kpi-value" data-money-to="${s.montantEncaisse}">0</div>
                    <div class="kpi-sub"><strong>${s.totaux.contrats}</strong> contrats signés</div></div>
            </div>
            <div class="grid-2">
                <section class="panel">
                    <div class="panel-head"><h3>État de la flotte</h3><span class="muted">${totalV} véhicule(s)</span></div>
                    <div class="panel-body">
                        <div class="stack-bar">${Object.keys(vs).map((k) =>
                            `<span data-w="${totalV ? 100 * vs[k] / totalV : 0}" style="background:${fleetColors[k]}" title="${esc(label(k))}"></span>`).join('')}</div>
                        <div class="legend">${Object.keys(vs).map((k) => `
                            <div class="legend-item"><span class="lbl"><i style="background:${fleetColors[k]}"></i>${esc(label(k))}</span>
                            <span class="val">${vs[k]}</span></div>`).join('')}</div>
                    </div>
                </section>
                <section class="panel">
                    <div class="panel-head"><h3>Réservations par statut</h3><a class="btn btn-ghost btn-sm" href="#/reservations">Voir tout</a></div>
                    <div class="panel-body hbars">${Object.keys(rs).map((k) => `
                        <div class="hbar"><span>${esc(label(k))}</span>
                        <div class="track"><div class="fill" data-w="${100 * rs[k] / maxR}"></div></div>
                        <span class="v">${rs[k]}</span></div>`).join('')}</div>
                </section>
            </div>
            <div class="grid-2">
                <section class="panel">
                    <div class="panel-head"><h3>Réservations en cours et à venir</h3></div>
                    ${s.prochainesReservations.length ? `<ul class="list">${s.prochainesReservations.map((r) => `
                        <li><div><div class="primary">${esc(r.client)}</div>
                        <div class="secondary">${esc(r.vehicule)} · ${fmtDate(r.dateDebut)} → ${fmtDate(r.dateFin)}</div></div>${pill(r.statut)}</li>`).join('')}</ul>`
                        : '<div class="empty-mini">Aucune réservation à venir.</div>'}
                </section>
                <section class="panel">
                    <div class="panel-head"><h3>Maintenances en cours</h3><a class="btn btn-ghost btn-sm" href="#/maintenances">Voir tout</a></div>
                    ${s.maintenancesEnCours.length ? `<ul class="list">${s.maintenancesEnCours.map((m) => `
                        <li><div><div class="primary">${esc(m.description)}</div>
                        <div class="secondary">${esc(m.vehicule)}</div></div><span class="muted">depuis le ${fmtDate(m.dateDebut)}</span></li>`).join('')}</ul>`
                        : '<div class="empty-mini">Aucun véhicule en maintenance.</div>'}
                </section>
            </div>
            <p class="muted" style="font-size:12.5px">Données au ${esc(longDate)}.</p>`;

        view.querySelectorAll('[data-count-to]').forEach((el) => countUp(el, Number(el.dataset.countTo), (v) => integer.format(Math.round(v))));
        view.querySelectorAll('[data-money-to]').forEach((el) => countUp(el, Number(el.dataset.moneyTo), (v) => money.format(v)));
        requestAnimationFrame(() => requestAnimationFrame(() =>
            view.querySelectorAll('[data-w]').forEach((el) => { el.style.width = el.dataset.w + '%'; })));
    }

    // ------------------------------------------------------------------ Resource list
    function renderResource(key) {
        const res = RESOURCES[key];
        const admin = state.me.admin;
        setTitle(res.group, res.title);
        state.view = {key, search: '', filter: '', sort: res.defaultSort ?? null};

        const filterOptions = res.filter
            ? `<select id="filter"><option value="">${res.filter.label}</option>${(state.enums[res.filter.enum] ?? [])
                .map((v) => `<option value="${v}">${esc(label(v))}</option>`).join('')}</select>` : '';
        $('#view').innerHTML = `
            <div class="toolbar">
                <label class="search">${icon('search')}<input id="search" type="search" placeholder="Rechercher dans ${res.title.toLowerCase()}" autocomplete="off"></label>
                ${filterOptions}
                <span class="total" id="total"></span>
                <span class="spacer"></span>
                ${admin ? `<button class="btn btn-primary" id="create" type="button">${newWord(res)} ${res.singular}</button>`
                        : '<span class="readonly-note">Consultation seule (rôle USER)</span>'}
            </div>
            <section class="panel">
                <div class="table-wrap"><table>
                    <thead><tr>${res.columns.map((c) => `<th data-key="${c.key}" class="${c.sortable === false ? '' : 'sortable'} ${c.right ? 'right' : ''}">${c.label}<span class="arrow">↑</span></th>`).join('')}
                    ${admin ? '<th></th>' : ''}</tr></thead>
                    <tbody id="rows">${skeletonRows(res.columns.length + (admin ? 1 : 0))}</tbody>
                </table></div>
            </section>`;

        $('#search').addEventListener('input', (e) => { state.view.search = e.target.value.trim().toLowerCase(); renderRows(); });
        $('#filter')?.addEventListener('change', (e) => { state.view.filter = e.target.value; renderRows(); });
        $('#create')?.addEventListener('click', () => openForm(key, null));
        document.querySelectorAll('th.sortable').forEach((th) => th.addEventListener('click', () => {
            const current = state.view.sort;
            state.view.sort = {key: th.dataset.key, dir: current?.key === th.dataset.key ? -current.dir : 1};
            renderRows();
        }));
        $('#rows').addEventListener('click', onRowClick);

        load(key, true).then(() => { if (state.route === key) renderRows(); }).catch(handleError);
    }

    function skeletonRows(cols) {
        const row = `<tr class="skeleton">${'<td><div class="bone"></div></td>'.repeat(cols)}</tr>`;
        return row.repeat(5);
    }

    function sortValue(row, key) {
        const v = row[key];
        if (v === null || v === undefined) return '';
        if (typeof v === 'number') return v;
        if (/^-?\d+(\.\d+)?$/.test(v)) return Number(v);
        return String(v).toLowerCase();
    }

    function renderRows() {
        const {key, search, filter, sort} = state.view;
        const res = RESOURCES[key];
        const admin = state.me.admin;
        let rows = state.data[key] ?? [];
        if (filter) rows = rows.filter((r) => r[res.filter.key] === filter);
        if (search) rows = rows.filter((r) => Object.values(r).some((v) =>
            v !== null && (Array.isArray(v) ? v.join(' ') : String(v) + ' ' + label(v)).toLowerCase().includes(search)));
        if (sort) {
            rows = [...rows].sort((a, b) => {
                const x = sortValue(a, sort.key), y = sortValue(b, sort.key);
                return (x > y ? 1 : x < y ? -1 : 0) * sort.dir;
            });
        }
        document.querySelectorAll('th[data-key]').forEach((th) => {
            th.classList.toggle('sorted', sort?.key === th.dataset.key);
            th.classList.toggle('desc', sort?.key === th.dataset.key && sort.dir < 0);
        });
        const all = (state.data[key] ?? []).length;
        $('#total').textContent = rows.length === all ? `${all} élément(s)` : `${rows.length} sur ${all}`;

        const cols = res.columns.length + (admin ? 1 : 0);
        if (!rows.length) {
            $('#rows').innerHTML = `<tr><td colspan="${cols}"><div class="empty">
                <h3>${all ? 'Aucun résultat' : `Aucun ${res.feminine ? 'élément' : res.singular} pour le moment`}</h3>
                <p>${all ? 'Modifiez la recherche ou le filtre.' : (admin ? 'Créez le premier pour commencer.' : 'Un administrateur peut en ajouter.')}</p>
                ${!all && admin ? `<button class="btn btn-primary" type="button" data-create>${newWord(res)} ${res.singular}</button>` : ''}
            </div></td></tr>`;
            return;
        }
        $('#rows').innerHTML = rows.map((r, i) => `
            <tr class="clickable" data-id="${r.id}" style="--i:${Math.min(i, 20)}">
                ${res.columns.map((c) => `<td class="${c.right ? 'right' : ''}">${c.render(r)}</td>`).join('')}
                ${admin ? `<td class="actions">
                    <button class="btn btn-ghost btn-sm" type="button" data-edit>Modifier</button>
                    <button class="btn btn-ghost btn-sm btn-link-danger" type="button" data-delete>Supprimer</button></td>` : ''}
            </tr>`).join('');
    }

    async function onRowClick(e) {
        const key = state.view.key;
        if (e.target.closest('[data-create]')) return openForm(key, null);
        const tr = e.target.closest('tr[data-id]');
        if (!tr) return;
        const row = state.data[key].find((r) => String(r.id) === tr.dataset.id);
        if (e.target.closest('[data-delete]')) return deleteRow(key, row);
        openForm(key, row);
    }

    async function deleteRow(key, row) {
        const res = RESOURCES[key];
        const ok = await confirmDialog(`Supprimer ${article(res)}${article(res).endsWith('’') ? '' : ' '}${res.singular} ?`,
            (res.deleteNote ? res.deleteNote + ' ' : '') + 'Cette action est définitive.');
        if (!ok) return;
        try {
            await api('DELETE', `/console/${key}/${row.id}`);
            toast(`${res.singular.charAt(0).toUpperCase() + res.singular.slice(1)} supprimé${res.feminine ? 'e' : ''}.`);
            state.data = {};                 // related counts may have changed (cascade)
            await load(key, true);
            renderRows();
        } catch (err) { handleError(err); }
    }

    // ------------------------------------------------------------------ Form (drawer)
    let drawerContext = null;

    async function openForm(key, row) {
        const res = RESOURCES[key];
        const admin = state.me.admin;
        const editing = row !== null;
        drawerContext = admin ? {submit: (form) => saveResource(key, row, form)} : null;
        $('#drawer-kicker').textContent = res.title;
        $('#drawer-title').textContent = !admin ? `Fiche ${res.singular}`
            : editing ? `Modifier ${article(res)}${article(res).endsWith('’') ? '' : ' '}${res.singular}` : `${newWord(res)} ${res.singular}`;
        const form = $('#drawer-form');
        form.innerHTML = '<div class="full skeleton"><div class="bone" style="width:50%"></div></div>';
        $('#drawer-foot').innerHTML = admin
            ? `<button class="btn btn-ghost" type="button" data-close>Annuler</button>
               <button class="btn btn-primary" type="submit" form="drawer-form">${editing ? 'Enregistrer' : 'Créer'}</button>`
            : '<button class="btn" type="button" data-close>Fermer</button>';
        openLayer($('#drawer-layer'));

        try {
            const refs = [...new Set(res.fields.filter((f) => f.ref).map((f) => f.ref))];
            await Promise.all(refs.map((r) => load(r, true)));
        } catch (err) { closeLayer($('#drawer-layer')); return handleError(err); }

        form.innerHTML = '<p class="form-error full" id="drawer-error" hidden></p>'
            + (editing ? `<div class="info-box">Identifiant technique : <span class="mono">${row.id}</span></div>` : '')
            + res.fields.map((f) => fieldHtml(f, editing ? row[f.key] : (typeof f.default === 'function' ? f.default() : f.default), !admin)).join('');
        form.querySelector('input:not([type=checkbox]), select, textarea')?.focus();
    }

    function fieldHtml(f, value, readonly) {
        const dis = readonly ? 'disabled' : '';
        const req = f.required ? 'required' : '';
        const cls = 'field' + (f.full ? ' full' : '');
        const lbl = `<span>${esc(f.label)}${f.required && !readonly ? '<span class="req">*</span>' : ''}</span>`;
        const help = f.help ? `<span class="help">${esc(f.help)}</span>` : '';
        const v = value ?? '';
        switch (f.type) {
            case 'enum':
                return `<label class="${cls}">${lbl}<select name="${f.key}" ${req} ${dis}>
                    ${f.required ? '' : '<option value="">-</option>'}
                    ${!v && f.required ? '<option value="" disabled selected>Choisir…</option>' : ''}
                    ${(state.enums[f.enum] ?? []).map((o) => `<option value="${o}" ${o === v ? 'selected' : ''}>${esc(label(o))}</option>`).join('')}
                </select>${help}</label>`;
            case 'ref': {
                const res = RESOURCES[f.ref];
                const opts = state.data[f.ref] ?? [];
                return `<label class="${cls}">${lbl}<select name="${f.key}" ${req} ${dis}>
                    <option value="" ${f.required ? 'disabled' : ''} ${v === '' ? 'selected' : ''}>${f.required ? 'Choisir…' : 'Aucune'}</option>
                    ${opts.map((o) => `<option value="${o.id}" ${o.id === v ? 'selected' : ''}>${esc(res.option(o))}</option>`).join('')}
                </select>${help}${!opts.length ? `<span class="help">Aucun ${res.singular} disponible : créez-en d’abord dans « ${res.title} ».</span>` : ''}</label>`;
            }
            case 'multi': {
                const res = RESOURCES[f.ref];
                const selected = new Set(Array.isArray(value) ? value : []);
                const opts = state.data[f.ref] ?? [];
                return `<div class="${cls}">${lbl}<div class="chips">
                    ${opts.map((o) => `<label class="chip"><input type="checkbox" name="${f.key}" value="${o.id}" ${selected.has(o.id) ? 'checked' : ''} ${dis}><span>${esc(res.option(o))}</span></label>`).join('')
                        || `<span class="help">Aucun ${res.singular} défini.</span>`}
                </div>${help}</div>`;
            }
            case 'boolean':
                return `<div class="${cls}">${lbl}<label class="switch"><input type="checkbox" name="${f.key}" ${value ? 'checked' : ''} ${dis}>
                    <span class="track"></span><span>${value ? 'Oui' : 'Non'}</span></label>${help}</div>`;
            case 'textarea':
                return `<label class="${cls}">${lbl}<textarea name="${f.key}" ${req} ${dis}>${esc(v)}</textarea>${help}</label>`;
            case 'money':
                return `<label class="${cls}">${lbl}<input name="${f.key}" type="number" min="0" step="0.01" inputmode="decimal" value="${esc(v)}" ${req} ${dis}>${help}</label>`;
            case 'date':
                return `<label class="${cls}">${lbl}<input name="${f.key}" type="date" value="${esc(v)}" ${req} ${dis}>${help}</label>`;
            default:
                return `<label class="${cls}">${lbl}<input name="${f.key}" type="${f.type === 'email' ? 'email' : 'text'}" value="${esc(v)}"
                    ${f.max ? `maxlength="${f.max}"` : ''} ${f.placeholder ? `placeholder="${esc(f.placeholder)}"` : ''} ${req} ${dis}>${help}</label>`;
        }
    }

    function readForm(res, form) {
        const body = {};
        for (const f of res.fields) {
            if (f.type === 'multi') {
                body[f.key] = [...form.querySelectorAll(`[name="${f.key}"]:checked`)].map((i) => Number(i.value));
            } else if (f.type === 'boolean') {
                body[f.key] = form.querySelector(`[name="${f.key}"]`).checked;
            } else {
                const raw = form.querySelector(`[name="${f.key}"]`).value.trim();
                body[f.key] = raw === '' ? null : (f.type === 'ref' ? Number(raw) : raw);
            }
        }
        return body;
    }

    $('#drawer-form').addEventListener('change', (e) => {
        const sw = e.target.closest('.switch');
        if (sw) sw.querySelector('span:last-child').textContent = e.target.checked ? 'Oui' : 'Non';
        e.target.closest('.field')?.classList.remove('invalid');
    });

    /** Agency console: create or update an entity from the drawer form. */
    async function saveResource(key, row, form) {
        const res = RESOURCES[key];
        // Client-side check of required fields (the server checks again)
        const missing = res.fields.filter((f) => f.required && f.type !== 'multi' && f.type !== 'boolean'
            && !form.querySelector(`[name="${f.key}"]`).value.trim());
        form.querySelectorAll('.field').forEach((el) => el.classList.remove('invalid'));
        if (missing.length) {
            missing.forEach((f) => form.querySelector(`[name="${f.key}"]`).closest('.field').classList.add('invalid'));
            throw new ApiError(400, 'Complétez les champs obligatoires : ' + missing.map((f) => f.label).join(', ') + '.');
        }
        const body = readForm(res, form);
        if (row) await api('PUT', `/console/${key}/${row.id}`, {...body, id: row.id});
        else await api('POST', `/console/${key}`, body);
        closeLayer($('#drawer-layer'));
        toast(row ? 'Modifications enregistrées.' : `${res.singular.charAt(0).toUpperCase() + res.singular.slice(1)} créé${res.feminine ? 'e' : ''}.`);
        state.data = {};
        await load(key, true);
        if (state.route === key) renderRows();
    }

    /** The drawer is shared: its context says what submitting does (console save or customer checkout). */
    $('#drawer-form').addEventListener('submit', async (e) => {
        e.preventDefault();
        if (!drawerContext) return;
        const form = e.currentTarget;
        const errorBox = $('#drawer-error');
        const done = busy($('#drawer-foot [type=submit]'), 'Enregistrement');
        try {
            errorBox.hidden = true;
            await drawerContext.submit(form);
        } catch (err) {
            if (err.status === 401) return handleError(err);
            errorBox.textContent = err.message;
            errorBox.hidden = false;
            form.scrollTop = 0;
        } finally {
            done();
        }
    });

    // ------------------------------------------------------------------ Customer space
    const iso = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
    const addDays = (isoDate, n) => { const d = parseDate(isoDate); d.setDate(d.getDate() + n); return iso(d); };
    const search = {debut: null, fin: null, categorie: ''};

    async function renderCatalogue() {
        setTitle('Espace client', 'Louer un véhicule');
        if (!search.debut || search.debut < today()) {
            search.debut = addDays(today(), 1);
            search.fin = addDays(today(), 3);
        }
        $('#view').innerHTML = `
            <section class="panel search-panel">
                <form id="search-form" class="search-grid" novalidate>
                    <label class="field"><span>Départ</span><input type="date" name="debut" min="${today()}" value="${search.debut}" required></label>
                    <label class="field"><span>Retour</span><input type="date" name="fin" min="${today()}" value="${search.fin}" required></label>
                    <label class="field"><span>Catégorie</span><select name="categorie">
                        <option value="">Toutes</option>
                        ${(state.enums.CategorieVehicule ?? []).map((c) => `<option value="${c}" ${c === search.categorie ? 'selected' : ''}>${esc(label(c))}</option>`).join('')}
                    </select></label>
                    <button class="btn btn-primary" type="submit">Rechercher</button>
                </form>
            </section>
            <div class="results-head"><h2 id="results-title"></h2><span class="muted" id="results-sub"></span></div>
            <div class="offers" id="offers">${'<div class="panel offer skeleton"><div class="bone" style="width:60%"></div><div class="bone" style="width:40%;margin-top:10px"></div><div class="bone" style="height:30px;margin-top:40px"></div></div>'.repeat(3)}</div>`;

        const form = $('#search-form');
        form.addEventListener('change', (e) => {
            if (e.target.name === 'debut' && form.fin.value < e.target.value) form.fin.value = e.target.value;
            form.fin.min = form.debut.value;
        });
        form.addEventListener('submit', (e) => {
            e.preventDefault();
            search.debut = form.debut.value;
            search.fin = form.fin.value;
            search.categorie = form.categorie.value;
            loadOffers();
        });
        loadOffers();
    }

    async function loadOffers() {
        const params = new URLSearchParams({debut: search.debut, fin: search.fin});
        if (search.categorie) params.set('categorie', search.categorie);
        let offers;
        try {
            offers = await api('GET', '/client/vehicules?' + params);
        } catch (err) {
            $('#offers').innerHTML = '';
            $('#results-title').textContent = 'Recherche impossible';
            $('#results-sub').textContent = err.message;
            return;
        }
        if (state.route !== 'louer') return;
        const nb = days(search.debut, search.fin);
        $('#results-title').textContent = `${offers.length} véhicule${offers.length > 1 ? 's' : ''} disponible${offers.length > 1 ? 's' : ''}`;
        $('#results-sub').textContent = `du ${fmtDate(search.debut)} au ${fmtDate(search.fin)} · ${nb} jour${nb > 1 ? 's' : ''}`;
        if (!offers.length) {
            $('#offers').innerHTML = `<div class="panel empty full-row"><h3>Aucun véhicule disponible</h3>
                <p>Essayez d’autres dates ou une autre catégorie.</p></div>`;
            return;
        }
        $('#offers').innerHTML = offers.map((o, i) => `
            <article class="panel offer" style="--i:${i}">
                <div class="offer-head">
                    <div><h3>${esc(o.marque)} ${esc(o.modele)}</h3>
                    <div class="muted">${o.agence ? esc(o.agence) : 'Agence à confirmer'}</div></div>
                    <span class="badge plain">${esc(label(o.categorie))}</span>
                </div>
                <div class="offer-tags">${o.equipements.length ? o.equipements.map((e) => `<span class="tag">${esc(e)}</span>`).join('') : '<span class="muted">Sans équipement en option</span>'}</div>
                <div class="offer-foot">
                    <div><div class="price">${fmtMoney(o.total)}</div>
                    <div class="muted">${fmtMoney(o.tarifJournalier)} / jour</div></div>
                    <button class="btn btn-primary" type="button" data-rent="${o.id}">Louer</button>
                </div>
            </article>`).join('');
        $('#offers').onclick = (e) => {
            const btn = e.target.closest('[data-rent]');
            if (btn) openCheckout(offers.find((o) => String(o.id) === btn.dataset.rent));
        };
    }

    function openCheckout(offer) {
        $('#drawer-kicker').textContent = 'Réservation';
        $('#drawer-title').textContent = `${offer.marque} ${offer.modele}`;
        const nb = offer.nbJours;
        $('#drawer-form').innerHTML = `
            <p class="form-error full" id="drawer-error" hidden></p>
            <dl class="summary full">
                <div><dt>Véhicule</dt><dd>${esc(offer.marque)} ${esc(offer.modele)} · <span class="mono">${esc(offer.immatriculation)}</span></dd></div>
                <div><dt>Agence de retrait</dt><dd>${esc(offer.agence ?? 'À confirmer')}${offer.ville ? ', ' + esc(offer.ville) : ''}</dd></div>
                <div><dt>Départ</dt><dd>${fmtDate(search.debut)}</dd></div>
                <div><dt>Retour</dt><dd>${fmtDate(search.fin)}</dd></div>
                <div><dt>Tarif</dt><dd>${fmtMoney(offer.tarifJournalier)} × ${nb} jour${nb > 1 ? 's' : ''}</dd></div>
                <div class="total"><dt>Total à payer</dt><dd>${fmtMoney(offer.total)}</dd></div>
            </dl>
            <label class="field full"><span>Mode de paiement<span class="req">*</span></span>
                <select name="modePaiement" required>
                    <option value="" disabled selected>Choisir…</option>
                    ${(state.enums.ModePaiement ?? []).map((m) => `<option value="${m}">${esc(label(m))}</option>`).join('')}
                </select></label>
            <label class="check full"><input type="checkbox" name="conditions">
                <span>J’accepte les conditions de location et je présenterai mon permis au retrait du véhicule.</span></label>
            <div class="info-box">Le paiement confirme la réservation. Le contrat est créé automatiquement et reste
                consultable dans « Mes locations ». Annulation possible jusqu’à la veille du départ.</div>`;
        $('#drawer-foot').innerHTML = `<button class="btn btn-ghost" type="button" data-close>Annuler</button>
            <button class="btn btn-primary" type="submit" form="drawer-form">Payer ${fmtMoney(offer.total)}</button>`;
        drawerContext = {
            submit: async (form) => {
                if (!form.modePaiement.value) throw new ApiError(400, 'Choisissez un mode de paiement.');
                if (!form.conditions.checked) throw new ApiError(400, 'Acceptez les conditions de location pour continuer.');
                const location = await api('POST', '/client/locations', {
                    vehiculeId: offer.id, dateDebut: search.debut, dateFin: search.fin, modePaiement: form.modePaiement.value
                });
                closeLayer($('#drawer-layer'));
                toast(`Location R-${location.id} confirmée. Paiement de ${fmtMoney(location.montantPaye)} enregistré.`);
                window.location.hash = '#/locations';
            }
        };
        openLayer($('#drawer-layer'));
    }

    async function renderLocations() {
        setTitle('Espace client', 'Mes locations');
        $('#view').innerHTML = `<section class="panel"><div class="table-wrap"><table><tbody>${skeletonRows(5)}</tbody></table></div></section>`;
        let rows;
        try {
            rows = await api('GET', '/client/locations');
        } catch (err) { return handleError(err); }
        if (state.route !== 'locations') return;
        if (!rows.length) {
            $('#view').innerHTML = `<section class="panel empty"><h3>Aucune location pour le moment</h3>
                <p>Choisissez vos dates et réservez un véhicule en quelques clics.</p>
                <a class="btn btn-primary" href="#/louer">Louer un véhicule</a></section>`;
            return;
        }
        const upcoming = rows.filter((r) => r.annulable).length;
        $('#view').innerHTML = `
            <div class="toolbar"><span class="total">${rows.length} location(s) · ${upcoming} à venir</span>
                <span class="spacer"></span><a class="btn btn-primary" href="#/louer">Nouvelle location</a></div>
            <section class="panel"><div class="table-wrap"><table>
                <thead><tr><th>N°</th><th>Véhicule</th><th>Période</th><th class="right">Montant</th><th>Paiement</th><th>Statut</th><th></th></tr></thead>
                <tbody>${rows.map((r, i) => `
                    <tr style="--i:${i}">
                        <td><span class="mono">R-${r.id}</span>${r.contratId ? `<span class="secondary mono">C-${r.contratId}</span>` : ''}</td>
                        <td>${two(r.vehicule, `${label(r.categorie)} · ${r.immatriculation}`)}</td>
                        <td>${two(`${fmtDate(r.dateDebut)} → ${fmtDate(r.dateFin)}`, `${r.nbJours} jour(s)${r.agence ? ' · ' + r.agence : ''}`)}</td>
                        <td class="right"><span class="num primary">${fmtMoney(r.montantTotal)}</span></td>
                        <td>${r.modePaiement ? two(fmtMoney(r.montantPaye), label(r.modePaiement)) : dash}</td>
                        <td>${pill(r.statut)}</td>
                        <td class="actions-visible">${r.annulable ? `<button class="btn btn-ghost btn-sm btn-link-danger" type="button" data-cancel-loc="${r.id}">Annuler</button>` : ''}</td>
                    </tr>`).join('')}</tbody>
            </table></div></section>`;
        $('#view').onclick = async (e) => {
            const btn = e.target.closest('[data-cancel-loc]');
            if (!btn) return;
            const ok = await confirmDialog(`Annuler la location R-${btn.dataset.cancelLoc} ?`,
                'Le véhicule sera remis à disposition sur ces dates. Le remboursement est traité par l’agence.',
                'Annuler la location');
            if (!ok) return;
            try {
                await api('POST', `/client/locations/${btn.dataset.cancelLoc}/annulation`);
                toast('Location annulée.');
                renderLocations();
            } catch (err) { handleError(err); }
        };
    }

    async function renderProfil() {
        setTitle('Espace client', 'Mon profil');
        let p;
        try {
            p = await api('GET', '/client/profil');
        } catch (err) { return handleError(err); }
        if (state.route !== 'profil') return;
        $('#view').innerHTML = `
            <section class="panel profile">
                <div class="panel-head"><h3>Informations personnelles</h3>
                    <span class="muted">Client depuis le ${fmtDate(p.dateInscription)} · ${p.nbLocations} location(s)</span></div>
                <form id="profil-form" class="panel-body form-grid" novalidate>
                    <label class="field"><span>Prénom<span class="req">*</span></span><input name="prenom" maxlength="50" value="${esc(p.prenom)}" required></label>
                    <label class="field"><span>Nom<span class="req">*</span></span><input name="nom" maxlength="50" value="${esc(p.nom)}" required></label>
                    <label class="field"><span>Email</span><input value="${esc(p.email)}" disabled><span class="help">Identifiant de connexion</span></label>
                    <label class="field"><span>Téléphone</span><input name="telephone" type="tel" maxlength="20" value="${esc(p.telephone ?? '')}"></label>
                    <label class="field"><span>N° de permis</span><input class="mono" value="${esc(p.numPermis)}" disabled>
                        <span class="help">Pour le modifier, contactez votre agence</span></label>
                    <p class="form-error full" id="profil-error" hidden></p>
                    <div class="full form-actions"><button class="btn btn-primary" type="submit">Enregistrer</button></div>
                </form>
            </section>`;
        $('#profil-form').addEventListener('submit', async (e) => {
            e.preventDefault();
            const form = e.currentTarget;
            const err = $('#profil-error');
            const done = busy(form.querySelector('[type=submit]'), 'Enregistrement');
            try {
                const updated = await api('PUT', '/client/profil',
                    {prenom: form.prenom.value, nom: form.nom.value, telephone: form.telephone.value});
                err.hidden = true;
                $('#user-name').textContent = `${updated.prenom} ${updated.nom}`;
                toast('Profil mis à jour.');
            } catch (error) {
                if (error.status === 401) return handleError(error);
                err.textContent = error.message;
                err.hidden = false;
            } finally {
                done();
            }
        });
    }

    $('#drawer-layer').addEventListener('click', (e) => {
        if (e.target.closest('[data-close]')) closeLayer($('#drawer-layer'));
    });
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape' && !$('#drawer-layer').hidden && $('#modal-layer').hidden) closeLayer($('#drawer-layer'));
    });

    // ------------------------------------------------------------------ Boot
    const saved = readAuth();
    if (saved) start(saved).catch(() => { writeAuth(null); showLogin(); });
    else showLogin();
})();
