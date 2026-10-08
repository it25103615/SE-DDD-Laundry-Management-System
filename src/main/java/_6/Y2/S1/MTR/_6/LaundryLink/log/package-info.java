/**
 * Order status logs (shared integration code): one {@code logs} row per status change of an
 * order, written by the features that move an order along and read back as its history.
 *
 * <p>Layout follows package-by-feature: entity, repository, service and controller live here.
 * {@code Log} refers to {@code Status} from the {@code status} package.
 */
package _6.Y2.S1.MTR._6.LaundryLink.log;
