# OpenSearch Stubs

I like having opensearch endpoints for easy search, so I made a few for sites that didn't have any decent search/suggest endpoints.
A public version is available at https://opensearch.martmists.com but I'd recommend self-hosting because if it gets marked as spam it might get blocked.

The format is always `https://{host}/{endpoint}/[search,suggest]?q={searchQuery}&lang={languageCode}`. The `lang` parameter is optional and defaults to `en`. Valid values for this depend on the search provider. 

XML description files are also provided. For an up-to-date list, simply query the main page.

### Supported sites

| Endpoint     | Website                                           | Description                                                                                                           |
|--------------|---------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------|
| `/pokemondb` | https://pokemondb.net/                            | Details on Pokemon game data. Categories are indexed on launch.                                                       |
| `/scryfall`  | https://scryfall.com/                             | Directly invokes the Scryfall API. Respects the 2 requests/500ms ratelimit.                                           |
| `/steam`     | https://store.steampowered.com/                   | Performs search over the Steam store.                                                                                 |
| `/python`    | https://docs.python.org/3/                        | Python 3 documentation, defaults to latest. Indexed on launch.                                                        |
| `/numpy`     | https://numpy.org/doc/stable/                     | Python NumPy documentation, defaults to latest. Indexed on launch.                                                    |
| `/scipy`     | https://docs.scipy.org/doc/scipy/                 | Python SciPy documentation, defaults to latest. Indexed on launch.                                                    |
| `/kotlin`    | https://kotlinlang.org/docs/                      | Kotlin documentation. Searches both stdlib and guides.                                                                |
| `/exposed`   | https://jetbrains.com/help/exposed/               | Exposed documentation. Searches both api and guides.                                                                  |
| `/ktor`      | https://ktor.io/docs/welcome.html                 | Ktor documentation. Searches both api and guides.                                                                     |
| `/composemp` | https://kotlinlang.org/api/compose-multiplatform/ | Compose Multiplatform documentation. Only provides suggestions, and search matches link to the exact page if present. |

### Contributing

Feel free to open PRs for additional sites! Though there are some requirements for this:

- Use API endpoints for suggestions, no scraping HTML.
- Do not include sites that require flaresolverr or similar services; It must be directly accessible.
