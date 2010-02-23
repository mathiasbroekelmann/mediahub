# Mediahub

playground for a modular architecture based on osgi and written in scala

## Current Features

* view renderer which resolves views by using the type hierarchy of the object to render and a classifier to find a view for. It also commes with a dsl to define views for any kind of object.
* link builder dsl based on the jcr 311 rest api to create links to any kind of resource instance.
* resource abstraction
* cache layer which allows to track dependencies for computed cache values. Those dependencies can be used to invalidate the cached values in a selective way.
* dsl for working with a java content repository (JCR)
* message source abstraction for localizing messages
* navigation api to define navigation points

## Current State

right now everything is just a proof of concept. But may parts are already working quite well

## Rendering views

Views can be defined by implementing org.mediahub.views.ViewModule and the function #configure(ViewBinder). AbstractViewModule simplifies this.
View classifiers are used to specify the view variation and the result type of the view. Any view must return a value of the type that was defined by the view classifier.

    import scala.xml.NodeSeq
    import org.mediahub.views._

    object html extends ViewClassifier[NodeSeq]
    object pagetitle extends ViewClassifier[String]
    object htmlbody extends ViewClassifier[NodeSeq]

    // just some content for which we want to render an html page
    trait Article {
        def title: String
        def text: String
    }

    // the view module 
    // you can have multiple view modules. For example for each domain type on module that contains the view variations for that domain type.
    class MyViewModule extends AbstractViewModule {
        def configure {
            // define the common parts of a html page for any kind of object
            bindView(html).of[Any] to { self =>
                <html>
                    <head>
                        <title>{render(self) as pagetitle}</title>
                    </head>
                    {render(self) as htmlbody} 
                </html>
            }

            // define what should be rendered for the pagetitle of an article
            bindView(pagetitle).of[Article] to { article =>
                article.title
            }

            // the html body of the article
            bindView(htmlbody).of[Article] to { article =>
                <body>
                    <p>{article.text}</p>
                </body>
            }
        }
    }

The view renderer can then be used to render the views.

    val renderer: ViewRenderer = ...
    val article: Article = ...

    val htmlOfArticle: NodeSeq = renderer.render(article) as html
    
