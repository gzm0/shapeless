/*
 * Copyright (c) 2013 Miles Sabin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package shapeless

import scala.language.experimental.macros

import scala.reflect.macros.blackbox

trait Lazy[T] {
  val value: T
}

object Lazy {
  def apply[T](t: => T) = new Lazy[T] {
    lazy val value = t
  }

  implicit def mkLazy[T]: Lazy[T] = macro mkLazyImpl[T]

  def mkLazyImpl[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[Lazy[T]] = {
    import c.universe._
    import Flag._

    val pendingSuperCall = Apply(Select(Super(This(typeNames.EMPTY), typeNames.EMPTY), termNames.CONSTRUCTOR), List())

    val lazySym = c.mirror.staticClass("shapeless.Lazy")

    val thisLazyTypeTree =
      AppliedTypeTree(
        Ident(lazySym),
        List(TypeTree(weakTypeOf[T]))
      )

    val recName = TermName(c.freshName)
    val className = TypeName(c.freshName)
    val recClass =
      ClassDef(Modifiers(FINAL), className, List(),
        Template(
          List(thisLazyTypeTree),
          noSelfType,
          List(
            DefDef(
              Modifiers(), termNames.CONSTRUCTOR, List(),
              List(List()),
              TypeTree(),
              Block(List(pendingSuperCall), Literal(Constant(())))
            ),

            // Implicit self-publication ties the knot
            ValDef(Modifiers(IMPLICIT), recName, thisLazyTypeTree, This(typeNames.EMPTY)),

            ValDef(Modifiers(LAZY), TermName("value"), TypeTree(weakTypeOf[T]),
              TypeApply(
                Select(Ident(definitions.PredefModule), TermName("implicitly")),
                List(TypeTree(weakTypeOf[T]))
              )

            )
          )
        )
      )

    val block =
      Block(
        List(recClass),
        Apply(Select(New(Ident(className)), termNames.CONSTRUCTOR), List())
      )

    c.Expr[Lazy[T]] { block }
  }
}
