# Notes de sécurité — AutoLoc (Spring Security)

Ce document décrit la configuration de sécurité mise en place dans `fr.ece.autoloc.config.SecurityConfig`
et répond aux questions de l'atelier.

## 1. Authentification et autorisation

- **Authentification** : vérifier *qui* fait la requête (identifiant + mot de passe).
  AutoLoc utilise **HTTP Basic** : les identifiants sont envoyés à chaque requête dans l'en-tête
  `Authorization: Basic <base64(identifiant:mot_de_passe)>`.
- **Autorisation** : vérifier *ce que* l'utilisateur authentifié a le droit de faire, selon son **rôle**
  (`USER` ou `ADMIN`).

## 2. Règles de routes

| Chemin | Règle | Accès |
|---|---|---|
| `/api/public/**` | `permitAll()` | tout le monde, même anonyme |
| `/api/admin/**` | `hasRole("ADMIN")` | ADMIN uniquement |
| `/api/**` | `hasAnyRole("USER", "ADMIN")` | tout utilisateur authentifié ayant un rôle applicatif |
| tout le reste | `authenticated()` | tout utilisateur authentifié |

Endpoints de démonstration (`SecurityDemoController`) et résultats obtenus :

| Requête | Anonyme | student (USER) | admin (ADMIN) |
|---|---|---|---|
| `GET /api/public/ping` | 200 | 200 | 200 |
| `GET /api/hello` | 401 | 200 | 200 |
| `GET /api/admin/report` | 401 | 403 | 200 |

Un mot de passe incorrect donne également **401**.

Captures Postman (dossier `docs/captures/`) :

- [401 — anonyme sur `/api/hello`](captures/401%20Unauthorized.png)
- [403 — `student` sur `/api/admin/report`](captures/403%20Forbidden.png)
- [200 — `admin` sur `/api/admin/report`](captures/200%20OK.png)

## 3. Comptes de démonstration

| Utilisateur | Mot de passe | Rôle |
|---|---|---|
| `student` | `student123` | USER |
| `admin` | `admin123` | ADMIN |

Ces comptes sont **réservés à l'exercice en local**. Ils ne doivent jamais être réutilisés ailleurs.

## 4. Réponses aux questions

### Pourquoi `/api/admin/**` doit-il être déclaré avant `/api/**` ?

Spring Security évalue les règles **dans l'ordre de déclaration** et applique la **première** qui
correspond. `/api/admin/report` correspond aussi à `/api/**` : si cette règle large était déclarée
en premier, un simple `USER` obtiendrait l'accès au rapport admin et la règle ADMIN ne serait jamais
évaluée. Il faut donc toujours aller **du plus spécifique au plus général** (et `anyRequest()` en dernier).

### Quelle différence entre 401 Unauthorized et 403 Forbidden ?

- **401 Unauthorized** : la requête n'est **pas authentifiée** (pas d'identifiants ou identifiants faux).
  Le serveur demande de s'identifier (en-tête `WWW-Authenticate: Basic`).
- **403 Forbidden** : l'utilisateur est **bien authentifié**, mais son rôle ne lui donne **pas le droit**
  d'accéder à la ressource. Se ré-identifier avec le même compte ne changera rien.

Exemple : `student` sur `/api/admin/report` → 403 ; anonyme sur la même route → 401.

### Pourquoi encoder les mots de passe avec BCrypt ?

- Un mot de passe stocké en clair est directement exploitable en cas de fuite (base volée, logs,
  sauvegarde) — et souvent réutilisé par l'utilisateur sur d'autres sites.
- **BCrypt** est une fonction de hachage **à sens unique** : on ne peut pas retrouver le mot de passe
  à partir du hash ; à la connexion, on hache le mot de passe saisi et on compare.
- Il ajoute un **sel aléatoire** (deux mots de passe identiques donnent deux hashs différents, ce qui
  rend inutiles les tables pré-calculées) et il est **volontairement lent** (facteur de coût réglable),
  ce qui rend les attaques par force brute très coûteuses.

### Pourquoi des utilisateurs en mémoire conviennent à l'exercice mais pas à la production ?

- Les comptes sont **écrits dans le code** : les modifier impose de recompiler et redéployer.
- Ils sont **perdus/recréés à chaque redémarrage** ; aucune inscription, modification de mot de passe
  ou désactivation de compte n'est possible.
- Les mots de passe de démonstration se retrouvent dans le **dépôt Git**.
- Avec plusieurs instances de l'application, chaque instance a sa propre liste, sans partage.

Ils sont en revanche parfaits pour un exercice : aucune base ni table supplémentaire, configuration
lisible en un seul fichier.

### Que faudrait-il pour charger les utilisateurs et rôles depuis MySQL ?

1. Une entité JPA `Utilisateur` (identifiant unique, mot de passe **haché BCrypt**, rôle(s), compte actif)
   et éventuellement une entité `Role` en `@ManyToMany`.
2. Un `UtilisateurRepository` avec une méthode `findByIdentifiant(String identifiant)`.
3. Une classe qui implémente `UserDetailsService` : elle charge l'utilisateur via le repository et le
   convertit en `UserDetails` (ou lève `UsernameNotFoundException`). Elle remplace le bean
   `InMemoryUserDetailsManager`.
4. Conserver le bean `PasswordEncoder` (BCrypt) : les mots de passe sont hachés **à l'inscription**,
   avant l'enregistrement en base.

## 5. Limites et points d'attention

- **HTTP Basic** transmet les identifiants à chaque requête, simplement encodés en Base64 (pas chiffrés) :
  en production, il faut impérativement **HTTPS**, et on préfère souvent des jetons (JWT, OAuth2).
- **CSRF** : la protection CSRF reste active par défaut. Elle ne gêne pas ces endpoints en `GET`, mais
  les futurs `POST`/`PUT`/`DELETE` d'une API sans état devront en tenir compte. Pour une application
  utilisée depuis un navigateur avec session, il faut revoir la protection CSRF avant de la désactiver.
- Une fois Spring Security ajouté, **toutes** les routes sont protégées par défaut (« secure by default ») :
  chaque nouvelle route publique doit être ouverte explicitement.
